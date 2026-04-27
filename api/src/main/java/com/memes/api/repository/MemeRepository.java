package com.memes.api.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Data access for the V2 normalized schema.
 * <p>
 * All write methods that touch multiple tables are {@link Transactional}; the
 * read methods are not — they rely on PostgreSQL MVCC for consistency.
 * <p>
 * Locales are passed as plain strings and bound with an explicit
 * {@code ?::locale_code} cast in SQL so the JDBC driver doesn't have to know
 * about the custom enum type.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class MemeRepository {

    public static final String DEFAULT_LOCALE = "en";
    private static final Set<String> VALID_SORT_COLUMNS = Set.of("score", "created_at", "title");

    private final JdbcTemplate jdbc;

    // ===== Stats / categories ==================================================

    public Optional<StatsRow> findStats() {
        try {
            StatsRow row = jdbc.queryForObject(
                "SELECT total_memes, total_categories, total_subreddits, top_category, indexed_at "
                    + "FROM stats_snapshot WHERE singleton = 1",
                (rs, i) -> StatsRow.builder()
                    .totalMemes(rs.getLong("total_memes"))
                    .totalCategories(rs.getLong("total_categories"))
                    .totalSubreddits(rs.getLong("total_subreddits"))
                    .topCategory(rs.getString("top_category"))
                    .indexedAt(toOffsetDateTime(rs.getTimestamp("indexed_at")))
                    .build()
            );
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<CategoryRow> findAllCategories(String locale) {
        String resolved = resolveLocale(locale);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT cc.category_id, cc.category, cc.count, cc.top_score, "
                + "       ct.locale, ct.name, ct.description "
                + "FROM   category_counts cc "
                + "LEFT   JOIN category_translations ct "
                + "       ON ct.category_id = cc.category_id AND ct.locale = ?::locale_code "
                + "ORDER  BY cc.count DESC, cc.category ASC",
            resolved
        );

        // Two-pass so the CategoryRow snapshot is built once with a complete list.
        Map<Long, CategoryRow.CategoryRowBuilder> builders = new LinkedHashMap<>();
        Map<Long, List<CategoryTranslationRow>> translationsById = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            long id = ((Number) r.get("category_id")).longValue();
            builders.computeIfAbsent(id, k -> CategoryRow.builder()
                .id(id)
                .slug((String) r.get("category"))
                .count(((Number) r.get("count")).intValue())
                .topScore(((Number) r.get("top_score")).intValue()));
            translationsById.computeIfAbsent(id, k -> new ArrayList<>());

            Optional.ofNullable((String) r.get("locale")).ifPresent(l ->
                translationsById.get(id).add(CategoryTranslationRow.builder()
                    .locale(l)
                    .name((String) r.get("name"))
                    .description((String) r.get("description"))
                    .build()));
        }
        return builders.entrySet().stream()
            .map(e -> e.getValue().translations(translationsById.get(e.getKey())).build())
            .toList();
    }

    // ===== Meme list / detail ==================================================

    public List<MemeRow> findAll(int offset, int limit,
                                 @Nullable String category,
                                 @Nullable String subreddit,
                                 String sort,
                                 String locale) {
        String orderCol = orderColumn(sort);
        StringBuilder sql = new StringBuilder(SELECT_MEME_BASE);
        sql.append(" WHERE m.deleted_at IS NULL");
        List<Object> params = new ArrayList<>();
        Optional.ofNullable(category).filter(c -> !c.isBlank()).ifPresent(c -> {
            sql.append(" AND c.slug = ?");
            params.add(c);
        });
        Optional.ofNullable(subreddit).filter(s -> !s.isBlank()).ifPresent(s -> {
            sql.append(" AND s.name = ?");
            params.add(s);
        });
        if ("title".equals(orderCol)) {
            sql.append(" ORDER BY (SELECT title FROM meme_translations mt2 "
                + "WHERE mt2.meme_id = m.id AND mt2.locale = ?::locale_code "
                + "LIMIT 1) ASC NULLS LAST, m.id DESC");
            params.add(resolveLocale(locale));
        } else if ("created_at".equals(orderCol)) {
            sql.append(" ORDER BY m.created_at DESC NULLS LAST, m.id DESC");
        } else {
            sql.append(" ORDER BY m.score DESC, m.id DESC");
        }
        sql.append(" LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbc.query(sql.toString(), MEME_ROW_MAPPER, params.toArray());
    }

    public int countFiltered(@Nullable String category, @Nullable String subreddit) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM memes m "
                + "JOIN categories c ON c.id = m.category_id "
                + "LEFT JOIN subreddits s ON s.id = m.subreddit_id "
                + "WHERE m.deleted_at IS NULL");
        List<Object> params = new ArrayList<>();
        Optional.ofNullable(category).filter(v -> !v.isBlank()).ifPresent(v -> {
            sql.append(" AND c.slug = ?");
            params.add(v);
        });
        Optional.ofNullable(subreddit).filter(v -> !v.isBlank()).ifPresent(v -> {
            sql.append(" AND s.name = ?");
            params.add(v);
        });
        Integer count = jdbc.queryForObject(sql.toString(), Integer.class, params.toArray());
        return Optional.ofNullable(count).orElse(0);
    }

    public Optional<MemeRow> findBySlugAndCategory(String slug, String category) {
        List<MemeRow> rows = jdbc.query(
            SELECT_MEME_BASE
                + " WHERE m.deleted_at IS NULL AND c.slug = ? AND m.slug = ?",
            MEME_ROW_MAPPER, category, slug);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    // ===== Search ==============================================================

    /** Result tuple of {@link #search(String, String, int, int)}. */
    public record SearchHit(MemeRow meme, float rank, long totalCount) {}

    public List<SearchHit> search(String query, String locale, int limit, int offset) {
        if (query == null || query.isBlank()) return List.of();
        String resolved = resolveLocale(locale);

        List<Map<String, Object>> hits = jdbc.queryForList(
            "SELECT meme_id, slug, category, title, description, score, rank, total_count "
                + "FROM   search_memes(?, ?::locale_code, ?, ?)",
            query, resolved, limit, offset);
        if (hits.isEmpty()) return List.of();

        List<Long> ids = hits.stream().map(h -> ((Number) h.get("meme_id")).longValue()).toList();
        Map<Long, MemeRow> byId = loadMemesByIds(ids);

        List<SearchHit> out = new ArrayList<>(hits.size());
        for (Map<String, Object> h : hits) {
            long id = ((Number) h.get("meme_id")).longValue();
            MemeRow row = byId.get(id);
            if (row == null) continue;
            out.add(new SearchHit(
                row,
                ((Number) h.get("rank")).floatValue(),
                ((Number) h.get("total_count")).longValue()
            ));
        }
        return out;
    }

    // ===== Lookup upsert helpers ==============================================

    public long upsertCategory(String slug) {
        return upsertReturningId(
            "INSERT INTO categories (slug) VALUES (?) "
                + "ON CONFLICT (slug) DO UPDATE SET slug = EXCLUDED.slug RETURNING id",
            slug);
    }

    public long upsertSubreddit(String name) {
        return upsertReturningId(
            "INSERT INTO subreddits (name) VALUES (?) "
                + "ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name RETURNING id",
            name);
    }

    public long upsertAuthor(String username) {
        return upsertReturningId(
            "INSERT INTO authors (username) VALUES (?) "
                + "ON CONFLICT (username) DO UPDATE SET username = EXCLUDED.username RETURNING id",
            username);
    }

    public long upsertTag(String slug) {
        return upsertReturningId(
            "INSERT INTO tags (slug) VALUES (?) "
                + "ON CONFLICT (slug) DO UPDATE SET slug = EXCLUDED.slug RETURNING id",
            slug);
    }

    private long upsertReturningId(String sql, String value) {
        Long id = jdbc.queryForObject(sql, Long.class, value);
        return Optional.ofNullable(id)
            .orElseThrow(() -> new IllegalStateException("Upsert returned null id for " + value));
    }

    // ===== Meme upsert =========================================================

    @Transactional
    public long upsertMeme(MemeUpsert u) {
        long categoryId = upsertCategory(u.categorySlug());
        Long subredditId = Optional.ofNullable(u.subredditName())
            .filter(s -> !s.isBlank())
            .map(this::upsertSubreddit)
            .orElse(null);
        Long authorId = Optional.ofNullable(u.authorUsername())
            .filter(a -> !a.isBlank())
            .map(this::upsertAuthor)
            .orElse(null);

        Timestamp createdAt = Optional.ofNullable(u.createdAt())
            .map(o -> Timestamp.from(o.toInstant()))
            .orElse(null);

        Long memeId = jdbc.queryForObject(
            "INSERT INTO memes "
                + "(category_id, slug, subreddit_id, author_id, default_locale, score, "
                + " source_url, post_url, created_at) "
                + "VALUES (?, ?, ?, ?, ?::locale_code, ?, ?, ?, ?) "
                + "ON CONFLICT (category_id, slug) DO UPDATE SET "
                + "  subreddit_id   = EXCLUDED.subreddit_id, "
                + "  author_id      = EXCLUDED.author_id, "
                + "  default_locale = EXCLUDED.default_locale, "
                + "  score          = EXCLUDED.score, "
                + "  source_url     = EXCLUDED.source_url, "
                + "  post_url       = EXCLUDED.post_url, "
                + "  created_at     = EXCLUDED.created_at, "
                + "  indexed_at     = now(), "
                + "  deleted_at     = NULL "
                + "RETURNING id",
            Long.class,
            categoryId, u.slug(), subredditId, authorId,
            resolveLocale(u.defaultLocale()), u.score(),
            u.sourceUrl(), u.postUrl(), createdAt);
        if (memeId == null) {
            throw new IllegalStateException("Upsert returned null meme id for " + u.slug());
        }

        upsertTranslations(memeId, u.translations());
        replaceImages(memeId, u.images());
        replaceTags(memeId, u.tagSlugs());
        return memeId;
    }

    private void upsertTranslations(long memeId, List<MemeTranslationRow> translations) {
        if (translations == null || translations.isEmpty()) return;
        for (MemeTranslationRow t : translations) {
            jdbc.update(
                "INSERT INTO meme_translations (meme_id, locale, title, description) "
                    + "VALUES (?, ?::locale_code, ?, ?) "
                    + "ON CONFLICT (meme_id, locale) DO UPDATE SET "
                    + "  title = EXCLUDED.title, description = EXCLUDED.description",
                memeId, t.locale(), t.title(), t.description());
        }
    }

    private void replaceImages(long memeId, List<MemeImageRow> images) {
        jdbc.update("DELETE FROM meme_images WHERE meme_id = ?", memeId);
        if (images == null || images.isEmpty()) return;
        for (MemeImageRow img : images) {
            jdbc.update(
                "INSERT INTO meme_images "
                    + "(meme_id, path, width, height, bytes, mime_type, position, is_primary) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                memeId, img.path(), img.width(), img.height(), img.bytes(),
                img.mimeType(), img.position(), img.isPrimary());
        }
    }

    private void replaceTags(long memeId, List<String> tagSlugs) {
        jdbc.update("DELETE FROM meme_tags WHERE meme_id = ?", memeId);
        if (tagSlugs == null || tagSlugs.isEmpty()) return;
        for (String slug : tagSlugs) {
            if (slug == null || slug.isBlank()) continue;
            long tagId = upsertTag(slug);
            jdbc.update(
                "INSERT INTO meme_tags (meme_id, tag_id) VALUES (?, ?) "
                    + "ON CONFLICT DO NOTHING",
                memeId, tagId);
        }
    }

    @Transactional
    public int upsertAll(List<MemeUpsert> memes) {
        if (memes == null || memes.isEmpty()) return 0;
        int n = 0;
        for (MemeUpsert m : memes) {
            try {
                upsertMeme(m);
                n++;
            } catch (RuntimeException e) {
                log.warn("Failed to upsert meme {}/{}: {}",
                    m.categorySlug(), m.slug(), e.getMessage());
            }
        }
        return n;
    }

    public void refreshStats() {
        jdbc.execute("SELECT refresh_stats()");
    }

    // ===== Helpers =============================================================

    private static String resolveLocale(@Nullable String locale) {
        return Optional.ofNullable(locale)
            .filter(s -> !s.isBlank())
            .orElse(DEFAULT_LOCALE);
    }

    private static String orderColumn(@Nullable String sort) {
        return Optional.ofNullable(sort)
            .filter(VALID_SORT_COLUMNS::contains)
            .orElse("score");
    }

    @Nullable
    private static OffsetDateTime toOffsetDateTime(@Nullable Timestamp ts) {
        return Optional.ofNullable(ts)
            .map(t -> OffsetDateTime.ofInstant(t.toInstant(), ZoneOffset.UTC))
            .orElse(null);
    }

    /**
     * Loads a set of memes by id (using the same column projection as
     * {@link #SELECT_MEME_BASE}) and indexes them for O(1) lookup.
     */
    private Map<Long, MemeRow> loadMemesByIds(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<MemeRow> rows = jdbc.query(
            SELECT_MEME_BASE + " WHERE m.id IN (" + placeholders + ")",
            MEME_ROW_MAPPER, ids.toArray());
        Map<Long, MemeRow> map = new HashMap<>(rows.size());
        rows.forEach(r -> map.put(r.id(), r));
        return map;
    }

    /**
     * Single-query projection that uses correlated subqueries with
     * jsonb_agg to bundle translations, images and tags. Each row is one meme.
     * Joining via lateral subqueries keeps the rowcount = N (no Cartesian
     * explosion when a meme has multiple translations or images).
     */
    private static final String SELECT_MEME_BASE = """
        SELECT
            m.id, m.slug, m.score, m.default_locale,
            m.created_at, m.indexed_at, m.source_url, m.post_url,
            c.slug AS category_slug,
            s.name AS subreddit_name,
            a.username AS author_username,
            COALESCE((
                SELECT jsonb_agg(jsonb_build_object(
                    'locale', mt.locale,
                    'title',  mt.title,
                    'description', mt.description
                ) ORDER BY mt.locale)
                FROM meme_translations mt WHERE mt.meme_id = m.id
            ), '[]'::jsonb) AS translations_json,
            COALESCE((
                SELECT jsonb_agg(jsonb_build_object(
                    'path',       mi.path,
                    'width',      mi.width,
                    'height',     mi.height,
                    'bytes',      mi.bytes,
                    'mime_type',  mi.mime_type,
                    'position',   mi.position,
                    'is_primary', mi.is_primary
                ) ORDER BY mi.position)
                FROM meme_images mi WHERE mi.meme_id = m.id
            ), '[]'::jsonb) AS images_json,
            COALESCE((
                SELECT array_agg(t.slug::text ORDER BY t.slug)
                FROM   meme_tags mtg
                JOIN   tags t ON t.id = mtg.tag_id
                WHERE  mtg.meme_id = m.id
            ), ARRAY[]::text[]) AS tag_slugs
        FROM   memes m
        JOIN   categories c ON c.id = m.category_id
        LEFT   JOIN subreddits s ON s.id = m.subreddit_id
        LEFT   JOIN authors    a ON a.id = m.author_id
        """;

    private static final RowMapper<MemeRow> MEME_ROW_MAPPER = (rs, i) -> {
        long id = rs.getLong("id");
        String translationsJson = rs.getString("translations_json");
        String imagesJson = rs.getString("images_json");
        Array tagsArray = rs.getArray("tag_slugs");
        List<String> tags = (tagsArray == null)
            ? List.of()
            : Arrays.asList((String[]) tagsArray.getArray());
        return MemeRow.builder()
            .id(id)
            .slug(rs.getString("slug"))
            .categorySlug(rs.getString("category_slug"))
            .defaultLocale(rs.getString("default_locale"))
            .subredditName(rs.getString("subreddit_name"))
            .authorUsername(rs.getString("author_username"))
            .score(rs.getInt("score"))
            .createdAt(toOffsetDateTime(rs.getTimestamp("created_at")))
            .sourceUrl(rs.getString("source_url"))
            .postUrl(rs.getString("post_url"))
            .indexedAt(toOffsetDateTime(rs.getTimestamp("indexed_at")))
            .translations(JsonAggregates.parseTranslations(translationsJson))
            .images(JsonAggregates.parseImages(imagesJson))
            .tagSlugs(tags)
            .build();
    };
}
