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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans the {@code memes/} corpus and orchestrates upserts into the V2
 * normalized schema.
 *
 * <p>Two MDX formats are supported:</p>
 * <ul>
 *   <li><b>V2 (translations map)</b> — single file with a {@code translations:} block
 *       keyed by locale code.</li>
 *   <li><b>Flat (pipeline output)</b> — one file per locale; the locale is encoded in
 *       the filename ({@code slug.es-AR.mdx}). The base file ({@code slug.mdx}) carries
 *       the English translation. Regional variants ({@code es-AR}) are mapped to their
 *       ISO 639-1 language code ({@code es}) for the DB enum.</li>
 * </ul>
 *
 * <p>During a full reindex, locale-specific sidecar files are automatically grouped
 * with their base file and merged into a single {@link MemeUpsert} with multiple
 * translations.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IndexerService {

    static final Set<String> ALLOWED_LOCALES = Set.of("en", "es", "pt", "fr", "de", "ar");
    static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    static final Pattern URL_PATTERN = Pattern.compile("^(https://|/).+", Pattern.CASE_INSENSITIVE);
    static final String DEFAULT_LOCALE = "en";

    /**
     * Matches locale-specific sidecar files: {@code slug.es-AR.mdx}, {@code slug.pt.mdx}.
     * Group 1 = base stem ({@code slug}), Group 2 = raw locale ({@code es-AR}).
     */
    static final Pattern LOCALE_MDX_PATTERN =
        Pattern.compile("^(.+)\\.([a-z]{2}(?:-[A-Z]{2})?)\\.mdx$");

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

        // Collect all MDX paths and group by canonical base path.
        // e.g. slug.mdx and slug.es-AR.mdx both map to the key slug.mdx.
        Map<Path, List<Path>> groups = new LinkedHashMap<>();
        try (var stream = Files.walk(root)) {
            stream
                .filter(p -> p.toString().endsWith(".mdx") && Files.isRegularFile(p))
                .sorted()
                .forEach(p -> {
                    Path baseKey = toBaseKey(p);
                    groups.computeIfAbsent(baseKey, k -> new ArrayList<>()).add(p);
                });
        } catch (IOException e) {
            errors.add("Error walking memes directory: " + e.getMessage());
            return Collections.emptyList();
        }

        List<MemeUpsert> upserts = new ArrayList<>();
        for (Map.Entry<Path, List<Path>> entry : groups.entrySet()) {
            try {
                parseMdxGroup(entry.getKey(), entry.getValue()).ifPresent(upserts::add);
            } catch (Exception e) {
                String msg = "Failed to parse " + entry.getKey() + ": " + e.getMessage();
                log.warn(msg);
                errors.add(msg);
            }
        }
        return upserts;
    }

    /**
     * Returns the canonical base path for any MDX file.
     * {@code parent/slug.es-AR.mdx} → {@code parent/slug.mdx}.
     */
    private Path toBaseKey(Path mdxPath) {
        String name = mdxPath.getFileName().toString();
        Matcher m = LOCALE_MDX_PATTERN.matcher(name);
        if (m.matches()) {
            return mdxPath.getParent().resolve(m.group(1) + ".mdx");
        }
        return mdxPath;
    }

    /**
     * Parses a group of MDX files that all belong to the same meme (base + locale
     * sidecars) and merges them into a single {@link MemeUpsert}.
     */
    Optional<MemeUpsert> parseMdxGroup(Path baseKey, List<Path> group) throws IOException {
        // Base file first so its metadata (images, score, etc.) takes precedence.
        List<Path> sorted = group.stream()
            .sorted(Comparator.comparing(p -> p.equals(baseKey) ? 0 : 1))
            .toList();

        MemeUpsert skeleton = null;
        List<MemeTranslationRow> allTranslations = new ArrayList<>();
        Set<String> seenLocales = new HashSet<>();

        for (Path p : sorted) {
            Optional<MemeUpsert> parsed;
            try {
                parsed = parseMdx(p);
            } catch (Exception e) {
                log.warn("Failed to parse {}: {}", p, e.getMessage());
                continue;
            }
            if (parsed.isEmpty()) continue;

            MemeUpsert u = parsed.get();
            if (skeleton == null) skeleton = u;

            for (MemeTranslationRow t : u.translations()) {
                if (seenLocales.add(t.locale())) {
                    allTranslations.add(t);
                }
            }
        }

        if (skeleton == null || allTranslations.isEmpty()) return Optional.empty();

        String defaultLocale = skeleton.defaultLocale();
        boolean hasDefault = allTranslations.stream().anyMatch(t -> defaultLocale.equals(t.locale()));
        if (!hasDefault) {
            log.warn("Skipping {}: no translation for default_locale '{}'", baseKey, defaultLocale);
            return Optional.empty();
        }

        return Optional.of(MemeUpsert.builder()
            .slug(skeleton.slug())
            .categorySlug(skeleton.categorySlug())
            .defaultLocale(defaultLocale)
            .subredditName(skeleton.subredditName())
            .authorUsername(skeleton.authorUsername())
            .score(skeleton.score())
            .createdAt(skeleton.createdAt())
            .sourceUrl(skeleton.sourceUrl())
            .postUrl(skeleton.postUrl())
            .images(skeleton.images())
            .tagSlugs(skeleton.tagSlugs())
            .translations(allTranslations)
            .build());
    }

    /**
     * Parses a single MDX file. Supports both formats:
     * <ul>
     *   <li><b>V2</b>: has a {@code translations:} map block.</li>
     *   <li><b>Flat</b>: has top-level {@code title} / {@code description}; locale is
     *       inferred from the filename ({@code slug.es-AR.mdx} → {@code es}).</li>
     * </ul>
     */
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

        List<MemeTranslationRow> translations;
        String defaultLocale;

        if (fm.get("translations") instanceof Map<?, ?>) {
            // V2 format: translations map keyed by locale code.
            defaultLocale = Optional.ofNullable(str(fm, "default_locale"))
                .filter(ALLOWED_LOCALES::contains)
                .orElse(DEFAULT_LOCALE);
            translations = parseTranslations(fm.get("translations"), mdxPath);
        } else {
            // Flat format: top-level title/description; locale from filename.
            String title = str(fm, "title");
            if (title == null || title.isBlank()) {
                log.warn("Skipping {}: no translations block and no title field", mdxPath);
                return Optional.empty();
            }
            String fileLocale = extractLocaleFromFilename(mdxPath.getFileName().toString())
                .map(IndexerService::normalizeLocale)
                .orElse(DEFAULT_LOCALE);
            defaultLocale = fileLocale;
            translations = List.of(MemeTranslationRow.builder()
                .locale(fileLocale)
                .title(title.trim())
                .description(str(fm, "description"))
                .build());
        }

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
            // Flat format: derive from the `image` field in frontmatter (already read
            // as the raw YAML map) or fall back to the base stem of the MDX filename.
            return List.of(MemeImageRow.builder()
                .path(deriveDefaultImagePath(category, slug, mdxPath))
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

    // ===== Locale helpers =====================================================

    /**
     * Extracts the raw locale string from a locale-specific MDX filename.
     * {@code "slug.es-AR.mdx"} → {@code Optional.of("es-AR")}.
     * {@code "slug.mdx"} → {@code Optional.empty()}.
     */
    static Optional<String> extractLocaleFromFilename(String filename) {
        Matcher m = LOCALE_MDX_PATTERN.matcher(filename);
        return m.matches() ? Optional.of(m.group(2)) : Optional.empty();
    }

    /**
     * Maps a raw locale string to the nearest supported ISO 639-1 code.
     * {@code "es-AR"} → {@code "es"}, {@code "pt-BR"} → {@code "pt"}.
     * Falls back to {@value DEFAULT_LOCALE} for unknown languages.
     */
    static String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) return DEFAULT_LOCALE;
        String lang = locale.split("[_\\-]", 2)[0].toLowerCase();
        return ALLOWED_LOCALES.contains(lang) ? lang : DEFAULT_LOCALE;
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

    /**
     * Derives the default image path when no explicit path is provided.
     * Strips any locale suffix from the MDX filename so that
     * {@code slug.es-AR.mdx} resolves to {@code memes/cat/slug.jpg}, not
     * {@code memes/cat/slug.es-AR.jpg}.
     */
    private String deriveDefaultImagePath(String category, String slug, Path mdxPath) {
        return Optional.ofNullable(mdxPath)
            .map(p -> {
                String name = p.getFileName().toString();
                Matcher lm = LOCALE_MDX_PATTERN.matcher(name);
                String base = lm.matches() ? lm.group(1)
                    : (name.endsWith(".mdx") ? name.substring(0, name.length() - 4) : slug);
                return "memes/" + category + "/" + base + ".jpg";
            })
            .orElseGet(() -> "memes/" + category + "/" + slug + ".jpg");
    }

    private String normalizeImagePath(String raw, String category, Path mdxPath, String slug) {
        if (raw == null || raw.isBlank()) {
            return deriveDefaultImagePath(category, slug, mdxPath);
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
