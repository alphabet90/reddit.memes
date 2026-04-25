package com.memes.api.service;

import com.memes.api.config.RedisConfig;
import com.memes.api.generated.model.MemeIndexRequest;
import com.memes.api.repository.MemeRecord;
import com.memes.api.repository.MemeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class IndexerService {

    @Value("${memes.root}")
    private String memesRoot;

    private final MemeRepository memeRepository;
    private final CacheManager cacheManager;

    @Async("reindexExecutor")
    public void reindexAsync(MemeIndexRequest body) {
        try {
            IndexResult result = Optional.ofNullable(body)
                .filter(r -> r.getSlug() != null && !r.getSlug().isBlank())
                .map(this::indexSingle)
                .orElseGet(this::reindex);
            log.info("Async reindex done: indexed={} durationMs={} errors={}",
                result.indexed(), result.durationMs(), result.errors());
        } catch (Exception e) {
            log.error("Async reindex failed", e);
        }
    }

    public IndexResult reindex() {
        long start = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();

        List<MemeRecord> memes = scanMdxFiles(errors);
        int indexed = memeRepository.upsertAll(memes);

        invalidateCaches();

        long duration = System.currentTimeMillis() - start;
        log.info("Reindex complete: {} memes indexed in {}ms ({} errors)", indexed, duration, errors.size());
        return new IndexResult(indexed, duration, errors);
    }

    public IndexResult indexSingle(MemeIndexRequest req) {
        long start = System.currentTimeMillis();

        String category = Optional.ofNullable(req.getCategory()).orElse("");
        String slug = Optional.ofNullable(req.getSlug()).orElse("");
        String imagePath = Optional.ofNullable(req.getImagePath())
            .filter(s -> !s.isBlank())
            .orElseGet(() -> "memes/" + category + "/" + slug + ".jpg");

        MemeRecord record = new MemeRecord(
            slug,
            category,
            Optional.ofNullable(req.getTitle()).orElse(""),
            req.getDescription(),
            req.getAuthor(),
            Optional.ofNullable(req.getSubreddit()).orElse("argentina"),
            Optional.ofNullable(req.getScore()).orElse(0),
            null,
            req.getSourceUrl(),
            req.getPostUrl(),
            imagePath,
            Optional.ofNullable(req.getTags()).orElse(List.of())
        );

        memeRepository.upsert(record);
        invalidateCaches();

        long duration = System.currentTimeMillis() - start;
        log.info("Single meme indexed: {}/{} in {}ms", category, slug, duration);
        return new IndexResult(1, duration, List.of());
    }

    private List<MemeRecord> scanMdxFiles(List<String> errors) {
        Path root = Paths.get(memesRoot);
        if (!Files.isDirectory(root)) {
            errors.add("Memes root not found: " + root.toAbsolutePath());
            return Collections.emptyList();
        }

        List<MemeRecord> records = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream
                .filter(p -> p.toString().endsWith(".mdx") && Files.isRegularFile(p))
                .forEach(mdxPath -> {
                    try {
                        parseMdx(mdxPath).ifPresent(records::add);
                    } catch (Exception e) {
                        String msg = "Failed to parse " + mdxPath + ": " + e.getMessage();
                        log.warn(msg);
                        errors.add(msg);
                    }
                });
        } catch (IOException e) {
            errors.add("Error walking memes directory: " + e.getMessage());
        }
        return records;
    }

    Optional<MemeRecord> parseMdx(Path mdxPath) throws IOException {
        List<String> lines = Files.readAllLines(mdxPath);
        if (lines.isEmpty() || !"---".equals(lines.get(0).trim())) {
            return Optional.empty();
        }

        int end = -1;
        for (int i = 1; i < lines.size(); i++) {
            if ("---".equals(lines.get(i).trim())) {
                end = i;
                break;
            }
        }
        if (end < 0) return Optional.empty();

        String yamlBlock = String.join("\n", lines.subList(1, end));
        Yaml yaml = new Yaml();
        Map<String, Object> fm = yaml.load(yamlBlock);
        if (fm == null || fm.isEmpty()) return Optional.empty();

        String category = str(fm, "category");
        String slug = str(fm, "slug");
        if (category == null || category.isBlank() || slug == null || slug.isBlank()) {
            return Optional.empty();
        }

        String imagePath = normalizeImagePath(str(fm, "image"), category, mdxPath);
        List<String> tags = parseTags(fm.get("tags"));

        return Optional.of(new MemeRecord(
            slug,
            category,
            Optional.ofNullable(str(fm, "title")).orElse(""),
            str(fm, "description"),
            str(fm, "author"),
            Optional.ofNullable(str(fm, "subreddit")).orElse(""),
            toInt(fm.get("score")),
            str(fm, "created_at"),
            str(fm, "source_url"),
            str(fm, "post_url"),
            imagePath,
            tags
        ));
    }

    private void invalidateCaches() {
        List.of(
            RedisConfig.CACHE_STATS, RedisConfig.CACHE_CATEGORIES,
            RedisConfig.CACHE_MEME_LIST, RedisConfig.CACHE_MEME,
            RedisConfig.CACHE_SEARCH
        ).forEach(name ->
            Optional.ofNullable(cacheManager.getCache(name)).ifPresent(c -> c.clear())
        );
    }

    private String normalizeImagePath(String raw, String category, Path mdxPath) {
        if (raw == null) {
            String mdxName = mdxPath.getFileName().toString();
            String base = mdxName.substring(0, mdxName.length() - 4);
            return "memes/" + category + "/" + base + ".jpg";
        }
        if (raw.startsWith("./")) {
            return "memes/" + category + "/" + raw.substring(2);
        }
        return raw;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseTags(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private String str(Map<String, Object> fm, String key) {
        return Optional.ofNullable(fm.get(key)).map(Object::toString).orElse(null);
    }

    private int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return 0;
    }
}
