package com.memes.api.service;

import com.memes.api.config.RedisConfig;
import com.memes.api.generated.model.LocaleCode;
import com.memes.api.generated.model.MemeImage;
import com.memes.api.generated.model.MemeIndexRequest;
import com.memes.api.generated.model.MemeTranslation;
import com.memes.api.repository.MemeImageRow;
import com.memes.api.repository.MemeRepository;
import com.memes.api.repository.MemeTranslationRow;
import com.memes.api.repository.MemeUpsert;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Scans the {@code memes/} corpus and orchestrates upserts into the V2
 * normalized schema. The MDX frontmatter format (see project docs) carries:
 * <pre>
 *   default_locale: en
 *   translations:
 *     en: { title: ..., description: ... }
 *     es: { title: ..., description: ... }
 *   images:
 *     - path: ./img.jpg
 *       is_primary: true
 *   tags: [foo, bar]
 * </pre>
 * After every reindex we call {@link MemeRepository#refreshStats()} to refresh
 * the materialized views feeding {@code GET /} and {@code GET /categories}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IndexerService {

    static final Set<String> ALLOWED_LOCALES = Set.of("en", "es", "pt", "fr", "de", "ar");
    static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    static final Pattern URL_PATTERN = Pattern.compile("^(https://|/).+", Pattern.CASE_INSENSITIVE);
    static final String DEFAULT_LOCALE = "en";

    @Value("${memes.root}")
    private String memesRoot;

    private final MemeRepository memeRepository;
    private final CacheManager cacheManager;

    // ===== Async dispatch =====================================================

    @Async("reindexExecutor")
    public void reindexAsync(MemeIndexRequest body) {
        try {
            IndexResult result = Optional.ofNullable(body)
                .filter(r -> r.getSlug() != null && !r.getSlug().isBlank())
                .map(this::indexSingle)
                .orElseGet(this::reindex);
            log.info("Async reindex done: indexed={} durationMs={} errors={}",
                result.indexed(), result.durationMs(), result.errors().size());
        } catch (Exception e) {
            log.error("Async reindex failed", e);
        }
    }

    // ===== Public entry points ================================================

    public IndexResult reindex() {
        long start = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();
        List<MemeUpsert> upserts = scanMdxFiles(errors);
        int indexed = memeRepository.upsertAll(upserts);
        memeRepository.refreshStats();
        invalidateCaches();
        long duration = System.currentTimeMillis() - start;
        log.info("Reindex complete: {} memes indexed in {}ms ({} errors)",
            indexed, duration, errors.size());
        return new IndexResult(indexed, duration, errors);
    }

    public IndexResult indexSingle(MemeIndexRequest req) {
        long start = System.currentTimeMillis();
        MemeUpsert upsert = fromIndexRequest(req);
        memeRepository.upsertMeme(upsert);
        memeRepository.refreshStats();
        invalidateCaches();
        long duration = System.currentTimeMillis() - start;
        log.info("Single meme indexed: {}/{} in {}ms",
            upsert.categorySlug(), upsert.slug(), duration);
        return new IndexResult(1, duration, List.of());
    }

    // ===== MDX scanning =======================================================

    private List<MemeUpsert> scanMdxFiles(List<String> errors) {
        Path root = Paths.get(memesRoot);
        if (!Files.isDirectory(root)) {
            errors.add("Memes root not found: " + root.toAbsolutePath());
            return Collections.emptyList();
        }
        List<MemeUpsert> upserts = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream
                .filter(p -> p.toString().endsWith(".mdx") && Files.isRegularFile(p))
                .forEach(mdxPath -> {
                    try {
                        parseMdx(mdxPath).ifPresent(upserts::add);
                    } catch (Exception e) {
                        String msg = "Failed to parse " + mdxPath + ": " + e.getMessage();
                        log.warn(msg);
                        errors.add(msg);
                    }
                });
        } catch (IOException e) {
            errors.add("Error walking memes directory: " + e.getMessage());
        }
        return upserts;
    }

    @SuppressWarnings("unchecked")
    Optional<MemeUpsert> parseMdx(Path mdxPath) throws IOException {
        List<String> lines = Files.readAllLines(mdxPath);
        if (lines.isEmpty() || !"---".equals(lines.get(0).trim())) {
            return Optional.empty();
        }
        int end = -1;
        for (int i = 1; i < lines.size(); i++) {
            if ("---".equals(lines.get(i).trim())) { end = i; break; }
        }
        if (end < 0) return Optional.empty();

        String yamlBlock = String.join("\n", lines.subList(1, end));
        Map<String, Object> fm = new Yaml().load(yamlBlock);
        if (fm == null || fm.isEmpty()) return Optional.empty();

        String category = str(fm, "category");
        String slug = str(fm, "slug");
        if (category == null || category.isBlank() || slug == null || slug.isBlank()) {
            log.warn("Skipping {}: missing category or slug", mdxPath);
            return Optional.empty();
        }
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            log.warn("Skipping {}: slug '{}' violates slug domain", mdxPath, slug);
            return Optional.empty();
        }
        if (!SLUG_PATTERN.matcher(category).matches()) {
            log.warn("Skipping {}: category '{}' violates slug domain", mdxPath, category);
            return Optional.empty();
        }

        String defaultLocale = Optional.ofNullable(str(fm, "default_locale"))
            .filter(ALLOWED_LOCALES::contains)
            .orElse(DEFAULT_LOCALE);

        List<MemeTranslationRow> translations = parseTranslations(fm.get("translations"), mdxPath);
        if (translations.isEmpty()) {
            log.warn("Skipping {}: no valid translations", mdxPath);
            return Optional.empty();
        }
        boolean hasDefault = translations.stream().anyMatch(t -> defaultLocale.equals(t.locale()));
        if (!hasDefault) {
            log.warn("Skipping {}: no translation for default_locale '{}'", mdxPath, defaultLocale);
            return Optional.empty();
        }

        List<MemeImageRow> images = parseImages(fm.get("images"), category, slug, mdxPath);
        List<String> tags = parseTags(fm.get("tags"));

        return Optional.of(MemeUpsert.builder()
            .slug(slug)
            .categorySlug(category)
            .defaultLocale(defaultLocale)
            .subredditName(sanitizeSubreddit(str(fm, "subreddit")))
            .authorUsername(sanitizeAuthor(str(fm, "author")))
            .score(toInt(fm.get("score")))
            .createdAt(parseDateTime(str(fm, "created_at")))
            .sourceUrl(sanitizeUrl(str(fm, "source_url"), mdxPath, "source_url"))
            .postUrl(sanitizeUrl(str(fm, "post_url"), mdxPath, "post_url"))
            .translations(translations)
            .images(images)
            .tagSlugs(tags)
            .build());
    }

    @SuppressWarnings("unchecked")
    private List<MemeTranslationRow> parseTranslations(Object raw, Path mdxPath) {
        if (!(raw instanceof Map<?, ?> map)) return List.of();
        List<MemeTranslationRow> out = new ArrayList<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String locale = String.valueOf(e.getKey());
            if (!ALLOWED_LOCALES.contains(locale)) {
                log.warn("Skipping unknown locale '{}' in {}", locale, mdxPath);
                continue;
            }
            if (!(e.getValue() instanceof Map<?, ?> body)) continue;
            String title = strFromMap((Map<Object, Object>) body, "title");
            if (title == null || title.isBlank()) {
                log.warn("Skipping locale '{}' in {}: missing title", locale, mdxPath);
                continue;
            }
            String description = strFromMap((Map<Object, Object>) body, "description");
            out.add(MemeTranslationRow.builder()
                .locale(locale)
                .title(title)
                .description(description)
                .build());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<MemeImageRow> parseImages(Object raw, String category, String slug, Path mdxPath) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            // Default to <slug>.jpg as primary image, mirroring V1's implicit behavior.
            return List.of(MemeImageRow.builder()
                .path("memes/" + category + "/" + slug + ".jpg")
                .position(0)
                .isPrimary(true)
                .build());
        }
        List<MemeImageRow> out = new ArrayList<>();
        boolean primaryAssigned = false;
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof Map<?, ?> body)) continue;
            Map<Object, Object> m = (Map<Object, Object>) body;
            String path = normalizeImagePath(strFromMap(m, "path"), category, mdxPath, slug);
            if (path == null) continue;
            boolean isPrimary = Boolean.TRUE.equals(m.get("is_primary"));
            if (isPrimary && primaryAssigned) {
                isPrimary = false;
                log.warn("Multiple primary images in {}; keeping the first", mdxPath);
            }
            primaryAssigned = primaryAssigned || isPrimary;
            out.add(MemeImageRow.builder()
                .path(path)
                .width(intOrNull(m.get("width")))
                .height(intOrNull(m.get("height")))
                .bytes(longOrNull(m.get("bytes")))
                .mimeType(strFromMap(m, "mime_type"))
                .position(i)
                .isPrimary(isPrimary)
                .build());
        }
        if (!primaryAssigned && !out.isEmpty()) {
            MemeImageRow first = out.get(0);
            out.set(0, MemeImageRow.builder()
                .path(first.path())
                .width(first.width())
                .height(first.height())
                .bytes(first.bytes())
                .mimeType(first.mimeType())
                .position(first.position())
                .isPrimary(true)
                .build());
        }
        return out;
    }

    // ===== From admin API =====================================================

    private MemeUpsert fromIndexRequest(MemeIndexRequest req) {
        String category = Optional.ofNullable(req.getCategory()).orElse("");
        String slug = Optional.ofNullable(req.getSlug()).orElse("");
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("slug violates slug domain: " + slug);
        }
        if (!SLUG_PATTERN.matcher(category).matches()) {
            throw new IllegalArgumentException("category violates slug domain: " + category);
        }
        LocaleCode defaultLocaleEnum = Optional.ofNullable(req.getDefaultLocale()).orElse(LocaleCode.EN);
        String defaultLocale = defaultLocaleEnum.getValue();

        List<MemeTranslationRow> translations = Optional.ofNullable(req.getTranslations())
            .orElse(List.of())
            .stream()
            .map(IndexerService::toTranslationRow)
            .toList();
        if (translations.isEmpty()) {
            throw new IllegalArgumentException("translations[] is required");
        }
        boolean hasDefault = translations.stream().anyMatch(t -> defaultLocale.equals(t.locale()));
        if (!hasDefault) {
            throw new IllegalArgumentException(
                "no translation for default_locale '" + defaultLocale + "'");
        }

        List<MemeImageRow> images = Optional.ofNullable(req.getImages())
            .filter(l -> !l.isEmpty())
            .map(list -> list.stream().map(IndexerService::toImageRow).toList())
            .orElseGet(() -> List.of(MemeImageRow.builder()
                .path("memes/" + category + "/" + slug + ".jpg")
                .position(0)
                .isPrimary(true)
                .build()));

        List<String> tags = Optional.ofNullable(req.getTags()).orElse(List.of());

        return MemeUpsert.builder()
            .slug(slug)
            .categorySlug(category)
            .defaultLocale(defaultLocale)
            .subredditName(sanitizeSubreddit(req.getSubreddit()))
            .authorUsername(sanitizeAuthor(req.getAuthor()))
            .score(Optional.ofNullable(req.getScore()).orElse(0))
            .createdAt(Optional.ofNullable(req.getCreatedAt()).orElse(null))
            .sourceUrl(sanitizeUrl(req.getSourceUrl(), null, "source_url"))
            .postUrl(sanitizeUrl(req.getPostUrl(), null, "post_url"))
            .translations(translations)
            .images(images)
            .tagSlugs(tags)
            .build();
    }

    private static MemeTranslationRow toTranslationRow(MemeTranslation t) {
        return MemeTranslationRow.builder()
            .locale(t.getLocale().getValue())
            .title(t.getTitle())
            .description(t.getDescription())
            .build();
    }

    private static MemeImageRow toImageRow(MemeImage img) {
        return MemeImageRow.builder()
            .path(img.getPath())
            .width(img.getWidth())
            .height(img.getHeight())
            .bytes(img.getBytes())
            .mimeType(img.getMimeType())
            .position(Optional.ofNullable(img.getPosition()).orElse(0))
            .isPrimary(Boolean.TRUE.equals(img.getIsPrimary()))
            .build();
    }

    // ===== Helpers ============================================================

    private void invalidateCaches() {
        List.of(
            RedisConfig.CACHE_STATS, RedisConfig.CACHE_CATEGORIES,
            RedisConfig.CACHE_MEME_LIST, RedisConfig.CACHE_MEME,
            RedisConfig.CACHE_SEARCH
        ).forEach(name ->
            Optional.ofNullable(cacheManager.getCache(name)).ifPresent(c -> c.clear())
        );
    }

    private String normalizeImagePath(String raw, String category, Path mdxPath, String slug) {
        if (raw == null || raw.isBlank()) {
            return Optional.ofNullable(mdxPath)
                .map(p -> {
                    String name = p.getFileName().toString();
                    String base = name.endsWith(".mdx") ? name.substring(0, name.length() - 4) : slug;
                    return "memes/" + category + "/" + base + ".jpg";
                })
                .orElseGet(() -> "memes/" + category + "/" + slug + ".jpg");
        }
        if (raw.startsWith("./")) {
            return "memes/" + category + "/" + raw.substring(2);
        }
        return raw;
    }

    private String sanitizeUrl(String raw, Path mdxPath, String field) {
        if (raw == null || raw.isBlank()) return null;
        if (URL_PATTERN.matcher(raw).matches() && raw.length() <= 2048) return raw;
        log.warn("Dropping invalid {} '{}' in {}", field, raw, mdxPath);
        return null;
    }

    private String sanitizeSubreddit(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.matches("^[A-Za-z0-9_]{1,21}$")) return raw;
        log.warn("Dropping invalid subreddit '{}'", raw);
        return null;
    }

    private String sanitizeAuthor(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if ("[deleted]".equals(raw) || "[removed]".equals(raw)) return raw;
        if (raw.matches("^[A-Za-z0-9_-]{1,20}$")) return raw;
        log.warn("Dropping invalid author '{}'", raw);
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseTags(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            String s = Optional.ofNullable(o).map(Object::toString).orElse("").trim();
            if (s.isEmpty()) continue;
            if (!SLUG_PATTERN.matcher(s).matches()) {
                log.warn("Dropping invalid tag '{}'", s);
                continue;
            }
            out.add(s);
        }
        return out;
    }

    private OffsetDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return OffsetDateTime.parse(s);
        } catch (Exception e) {
            log.warn("Could not parse datetime: {}", s);
            return null;
        }
    }

    private String str(Map<String, Object> fm, String key) {
        return Optional.ofNullable(fm.get(key)).map(Object::toString).orElse(null);
    }

    private String strFromMap(Map<Object, Object> map, String key) {
        return Optional.ofNullable(map.get(key)).map(Object::toString).orElse(null);
    }

    private int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private Integer intOrNull(Object v) {
        if (v instanceof Number n) return n.intValue();
        return null;
    }

    private Long longOrNull(Object v) {
        if (v instanceof Number n) return n.longValue();
        return null;
    }
}
