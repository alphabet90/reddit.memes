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
);

CREATE INDEX IF NOT EXISTS idx_memes_category  ON memes(category);
CREATE INDEX IF NOT EXISTS idx_memes_score     ON memes(score DESC);
CREATE INDEX IF NOT EXISTS idx_memes_subreddit ON memes(subreddit);
CREATE INDEX IF NOT EXISTS idx_memes_created   ON memes(created_at DESC);

CREATE VIRTUAL TABLE IF NOT EXISTS memes_fts USING fts5(
    slug,
    title,
    description,
    content='memes',
    content_rowid='rowid'
);

CREATE TRIGGER IF NOT EXISTS memes_ai AFTER INSERT ON memes BEGIN
    INSERT INTO memes_fts(rowid, slug, title, description)
    VALUES (new.rowid, new.slug, new.title, new.description);
END;

CREATE TRIGGER IF NOT EXISTS memes_au AFTER UPDATE ON memes BEGIN
    INSERT INTO memes_fts(memes_fts, rowid, slug, title, description)
    VALUES ('delete', old.rowid, old.slug, old.title, old.description);
    INSERT INTO memes_fts(rowid, slug, title, description)
    VALUES (new.rowid, new.slug, new.title, new.description);
END;

CREATE TRIGGER IF NOT EXISTS memes_ad AFTER DELETE ON memes BEGIN
    INSERT INTO memes_fts(memes_fts, rowid, slug, title, description)
    VALUES ('delete', old.rowid, old.slug, old.title, old.description);
END;

CREATE VIEW IF NOT EXISTS category_counts AS
    SELECT category,
           COUNT(*)   AS count,
           MAX(score) AS top_score
    FROM   memes
    GROUP  BY category
    ORDER  BY count DESC;
