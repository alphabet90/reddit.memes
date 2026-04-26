package com.memes.api.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class MemeRepository {

    private static final Logger log = LoggerFactory.getLogger(MemeRepository.class);
    private static final Set<String> VALID_SORT_COLUMNS = Set.of("score", "created_at", "title");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;

    public MemeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- Read ---------------------------------------------------------------

    public int countAll() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM memes", Integer.class);
        return count != null ? count : 0;
    }

    public int countCategories() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT category) FROM memes", Integer.class);
        return count != null ? count : 0;
    }

    public int countSubreddits() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT subreddit) FROM memes", Integer.class);
        return count != null ? count : 0;
    }

    public Optional<String> topCategory() {
        List<String> results = jdbc.queryForList(
            "SELECT category FROM memes GROUP BY category ORDER BY COUNT(*) DESC LIMIT 1",
            String.class);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public String findLastIndexedAt() {
        List<String> results = jdbc.queryForList(
            "SELECT MAX(indexed_at) FROM memes", String.class);
        return (results.isEmpty() || results.get(0) == null) ? null : results.get(0);
    }

    public List<CategoryRow> findAllCategories() {
        return jdbc.query(
            "SELECT category, COUNT(*) AS count, MAX(score) AS top_score " +
            "FROM memes GROUP BY category ORDER BY count DESC",
            (rs, i) -> new CategoryRow(
                rs.getString("category"),
                rs.getInt("count"),
                rs.getInt("top_score")
            )
        );
    }

    public List<MemeRecord> findAll(
            int offset, int limit,
            String category, String subreddit, String sort) {

        String col = VALID_SORT_COLUMNS.contains(sort) ? sort : "score";
        StringBuilder sql = new StringBuilder("SELECT * FROM memes WHERE 1=1");
        List<Object> params = new java.util.ArrayList<>();

        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        if (subreddit != null && !subreddit.isBlank()) {
            sql.append(" AND subreddit = ?");
            params.add(subreddit);
        }
        sql.append(" ORDER BY ").append(col).append(" DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbc.query(sql.toString(), memeRowMapper(), params.toArray());
    }

    public int countFiltered(String category, String subreddit) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM memes WHERE 1=1");
        List<Object> params = new java.util.ArrayList<>();

        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        if (subreddit != null && !subreddit.isBlank()) {
            sql.append(" AND subreddit = ?");
            params.add(subreddit);
        }
        Integer count = jdbc.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }

    public Optional<MemeRecord> findBySlugAndCategory(String slug, String category) {
        List<MemeRecord> results = jdbc.query(
            "SELECT * FROM memes WHERE slug = ? AND category = ?",
            memeRowMapper(), slug, category);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<MemeRecord> search(String query, int offset, int limit) {
        String tsq = buildTsQuery(query);
        if (tsq.isEmpty()) return List.of();
        return jdbc.query(
            "SELECT * FROM memes WHERE search_vector @@ to_tsquery('english', ?) " +
            "ORDER BY ts_rank(search_vector, to_tsquery('english', ?)) DESC LIMIT ? OFFSET ?",
            memeRowMapper(), tsq, tsq, limit, offset);
    }

    public int countSearch(String query) {
        String tsq = buildTsQuery(query);
        if (tsq.isEmpty()) return 0;
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM memes WHERE search_vector @@ to_tsquery('english', ?)",
            Integer.class, tsq);
        return count != null ? count : 0;
    }

    // ---- Write ---------------------------------------------------------------

    public void upsert(MemeRecord meme) {
        String tagsJson = toJson(meme.tags());
        jdbc.update(
            "INSERT INTO memes " +
            "(slug, category, title, description, author, subreddit, score, " +
            " created_at, source_url, post_url, image_path, tags, indexed_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT (slug, category) DO UPDATE SET " +
            "  title=EXCLUDED.title, description=EXCLUDED.description, " +
            "  author=EXCLUDED.author, subreddit=EXCLUDED.subreddit, " +
            "  score=EXCLUDED.score, created_at=EXCLUDED.created_at, " +
            "  source_url=EXCLUDED.source_url, post_url=EXCLUDED.post_url, " +
            "  image_path=EXCLUDED.image_path, tags=EXCLUDED.tags, " +
            "  indexed_at=EXCLUDED.indexed_at",
            meme.slug(), meme.category(), meme.title(), meme.description(),
            meme.author(), meme.subreddit(), meme.score(), meme.createdAt(),
            meme.sourceUrl(), meme.postUrl(), meme.imagePath(), tagsJson, nowIso()
        );
    }

    public int upsertAll(List<MemeRecord> memes) {
        if (memes.isEmpty()) return 0;
        String now = nowIso();
        int[][] counts = jdbc.batchUpdate(
            "INSERT INTO memes " +
            "(slug, category, title, description, author, subreddit, score, " +
            " created_at, source_url, post_url, image_path, tags, indexed_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT (slug, category) DO UPDATE SET " +
            "  title=EXCLUDED.title, description=EXCLUDED.description, " +
            "  author=EXCLUDED.author, subreddit=EXCLUDED.subreddit, " +
            "  score=EXCLUDED.score, created_at=EXCLUDED.created_at, " +
            "  source_url=EXCLUDED.source_url, post_url=EXCLUDED.post_url, " +
            "  image_path=EXCLUDED.image_path, tags=EXCLUDED.tags, " +
            "  indexed_at=EXCLUDED.indexed_at",
            memes,
            memes.size(),
            (ps, meme) -> {
                ps.setString(1, meme.slug());
                ps.setString(2, meme.category());
                ps.setString(3, meme.title());
                ps.setString(4, meme.description());
                ps.setString(5, meme.author());
                ps.setString(6, meme.subreddit());
                ps.setInt(7, meme.score());
                ps.setString(8, meme.createdAt());
                ps.setString(9, meme.sourceUrl());
                ps.setString(10, meme.postUrl());
                ps.setString(11, meme.imagePath());
                ps.setString(12, toJson(meme.tags()));
                ps.setString(13, now);
            }
        );
        return Arrays.stream(counts).flatMapToInt(Arrays::stream).sum();
    }

    // ---- Helpers -------------------------------------------------------------

    private RowMapper<MemeRecord> memeRowMapper() {
        return (rs, i) -> new MemeRecord(
            rs.getString("slug"),
            rs.getString("category"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getString("author"),
            rs.getString("subreddit"),
            rs.getInt("score"),
            rs.getString("created_at"),
            rs.getString("source_url"),
            rs.getString("post_url"),
            rs.getString("image_path"),
            fromJson(rs.getString("tags"))
        );
    }

    private String buildTsQuery(String query) {
        return Arrays.stream(query.trim().split("\\s+"))
            .filter(w -> !w.isEmpty())
            .map(w -> w.replaceAll("[^a-zA-Z0-9]", ""))
            .filter(w -> !w.isEmpty())
            .map(w -> w + ":*")
            .collect(Collectors.joining(" & "));
    }

    private static String nowIso() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private String toJson(List<String> tags) {
        if (tags == null || tags.isEmpty()) return "[]";
        try {
            return MAPPER.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return MAPPER.readValue(json, STRING_LIST);
        } catch (JsonProcessingException e) {
            log.warn("Could not parse tags JSON: {}", json);
            return Collections.emptyList();
        }
    }
}
