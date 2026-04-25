-- V1__init_schema.sql
-- Initial schema: memes table, indexes, FTS generated column, and category_counts view.

CREATE TABLE IF NOT EXISTS memes (
    slug          TEXT    NOT NULL,
    category      TEXT    NOT NULL,
    title         TEXT    NOT NULL DEFAULT '',
    description   TEXT,
    author        TEXT,
    subreddit     TEXT    NOT NULL DEFAULT 'argentina',
    score         INTEGER NOT NULL DEFAULT 0,
    created_at    TEXT,
    source_url    TEXT,
    post_url      TEXT,
    image_path    TEXT,
    tags          TEXT,
    indexed_at    TEXT    NOT NULL DEFAULT TO_CHAR(NOW() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
    search_vector tsvector GENERATED ALWAYS AS (
        to_tsvector('english',
            coalesce(title, '')       || ' ' ||
            coalesce(description, '') || ' ' ||
            coalesce(slug, ''))
    ) STORED,
    PRIMARY KEY (slug, category)
);

CREATE INDEX IF NOT EXISTS idx_memes_category  ON memes (category);
CREATE INDEX IF NOT EXISTS idx_memes_score     ON memes (score DESC);
CREATE INDEX IF NOT EXISTS idx_memes_subreddit ON memes (subreddit);
CREATE INDEX IF NOT EXISTS idx_memes_created   ON memes (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_memes_fts       ON memes USING gin (search_vector);

CREATE OR REPLACE VIEW category_counts AS
    SELECT   category,
             COUNT(*)   AS count,
             MAX(score) AS top_score
    FROM     memes
    GROUP BY category
    ORDER BY count DESC;
