package com.memes.api.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Executes SQLite DDL at startup. Uses JdbcTemplate.execute() per statement
 * to avoid delimiter issues with trigger bodies that contain semicolons.
 */
@Component
public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final JdbcTemplate jdbc;

    public DatabaseInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing SQLite schema");

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS memes (
                slug        TEXT NOT NULL,
                category    TEXT NOT NULL,
                title       TEXT NOT NULL DEFAULT '',
                description TEXT,
                author      TEXT,
                subreddit   TEXT NOT NULL DEFAULT 'argentina',
                score       INTEGER NOT NULL DEFAULT 0,
                created_at  TEXT,
                source_url  TEXT,
                post_url    TEXT,
                image_path  TEXT,
                tags        TEXT,
                indexed_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
                PRIMARY KEY (slug, category)
            )""");

        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_memes_category  ON memes(category)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_memes_score     ON memes(score DESC)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_memes_subreddit ON memes(subreddit)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_memes_created   ON memes(created_at DESC)");

        jdbc.execute("""
            CREATE VIRTUAL TABLE IF NOT EXISTS memes_fts USING fts5(
                slug,
                title,
                description,
                content='memes',
                content_rowid='rowid'
            )""");

        jdbc.execute("""
            CREATE TRIGGER IF NOT EXISTS memes_ai AFTER INSERT ON memes BEGIN
                INSERT INTO memes_fts(rowid, slug, title, description)
                VALUES (new.rowid, new.slug, new.title, new.description);
            END""");

        jdbc.execute("""
            CREATE TRIGGER IF NOT EXISTS memes_au AFTER UPDATE ON memes BEGIN
                INSERT INTO memes_fts(memes_fts, rowid, slug, title, description)
                VALUES ('delete', old.rowid, old.slug, old.title, old.description);
                INSERT INTO memes_fts(rowid, slug, title, description)
                VALUES (new.rowid, new.slug, new.title, new.description);
            END""");

        jdbc.execute("""
            CREATE TRIGGER IF NOT EXISTS memes_ad AFTER DELETE ON memes BEGIN
                INSERT INTO memes_fts(memes_fts, rowid, slug, title, description)
                VALUES ('delete', old.rowid, old.slug, old.title, old.description);
            END""");

        jdbc.execute("""
            CREATE VIEW IF NOT EXISTS category_counts AS
                SELECT category,
                       COUNT(*)   AS count,
                       MAX(score) AS top_score
                FROM   memes
                GROUP  BY category
                ORDER  BY count DESC""");

        log.info("SQLite schema ready");
    }
}
