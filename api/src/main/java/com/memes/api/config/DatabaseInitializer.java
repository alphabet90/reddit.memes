package com.memes.api.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final JdbcTemplate jdbc;

    public DatabaseInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing PostgreSQL schema");

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
                indexed_at  TEXT NOT NULL DEFAULT TO_CHAR(NOW() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                PRIMARY KEY (slug, category)
            )""");

        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_memes_category  ON memes(category)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_memes_score     ON memes(score DESC)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_memes_subreddit ON memes(subreddit)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_memes_created   ON memes(created_at DESC)");

        jdbc.execute("""
            ALTER TABLE memes ADD COLUMN IF NOT EXISTS search_vector tsvector
                GENERATED ALWAYS AS (
                    to_tsvector('english',
                        coalesce(title,'') || ' ' ||
                        coalesce(description,'') || ' ' ||
                        coalesce(slug,''))
                ) STORED""");

        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_memes_fts ON memes USING gin(search_vector)");

        jdbc.execute("""
            CREATE OR REPLACE VIEW category_counts AS
                SELECT category,
                       COUNT(*)   AS count,
                       MAX(score) AS top_score
                FROM   memes
                GROUP  BY category
                ORDER  BY count DESC""");

        log.info("PostgreSQL schema ready");
    }
}
