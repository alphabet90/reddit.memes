package com.memes.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memes.api.config.RedisConfig;
import com.memes.api.repository.MemeRecord;
import com.memes.api.repository.MemeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
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

@Service
public class IndexerService {

    private static final Logger log = LoggerFactory.getLogger(IndexerService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${memes.root}")
    private String memesRoot;

    private final MemeRepository memeRepository;
    private final CacheManager cacheManager;

    public IndexerService(MemeRepository memeRepository, CacheManager cacheManager) {
        this.memeRepository = memeRepository;
        this.cacheManager = cacheManager;
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

        // Find the closing ---
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

        // Normalize image path: "./slug.jpg" → "memes/{category}/slug.jpg"
        String imagePath = normalizeImagePath(str(fm, "image"), category, mdxPath);

        // tags: SnakeYAML parses YAML arrays as List<String> directly
        List<String> tags = parseTags(fm.get("tags"));

        return Optional.of(new MemeRecord(
            slug,
            category,
            nullToEmpty(str(fm, "title")),
            str(fm, "description"),
            str(fm, "author"),
            nullToEmpty(str(fm, "subreddit")),
            toInt(fm.get("score")),
            str(fm, "created_at"),
            str(fm, "source_url"),
            str(fm, "post_url"),
            imagePath,
            tags
        ));
    }

    private void invalidateCaches() {
        for (String name : List.of(
                RedisConfig.CACHE_STATS, RedisConfig.CACHE_CATEGORIES,
                RedisConfig.CACHE_MEME_LIST, RedisConfig.CACHE_MEME,
                RedisConfig.CACHE_SEARCH)) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        }
    }

    private String normalizeImagePath(String raw, String category, Path mdxPath) {
        if (raw == null) {
            // Derive from the .mdx file's sibling image
            String mdxName = mdxPath.getFileName().toString();
            String base = mdxName.substring(0, mdxName.length() - 4); // remove .mdx
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
            return list.stream()
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toList());
        }
        return Collections.emptyList();
    }

    private String str(Map<String, Object> fm, String key) {
        Object v = fm.get(key);
        return v != null ? v.toString() : null;
    }

    private String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return 0;
    }
}
