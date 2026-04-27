-- ============================================================================
-- V3__redesign_schema.sql
-- Memes API — robust, scalable, multilingual schema (replaces V1).
-- Target: PostgreSQL 14+
--
-- Goals vs V1:
--   * Normalize categories / subreddits / authors / tags (no more denormalized
--     strings repeated on every row).
--   * Per-locale title, description and FTS (V1 was hard-coded to 'english').
--   * Real types: TIMESTAMPTZ instead of TEXT, ENUM for locale, domains for
--     slugs and URLs that REJECT bad input at the DB layer (defense-in-depth
--     against weak app-side validation).
--   * Composite indexes that match the API's filter+sort patterns and support
--     keyset pagination.
--   * Materialized aggregates so GET /  and  GET /categories  are O(1).
--   * Soft delete + updated_at audit columns.
--
-- Note: BEGIN/COMMIT are deliberately omitted. Flyway 10 wraps each migration
-- in its own transaction; explicit BEGIN/COMMIT would prematurely close it and
-- break any future CREATE INDEX CONCURRENTLY or REFRESH MATERIALIZED VIEW
-- CONCURRENTLY usage.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Extensions
-- ----------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS pg_trgm;     -- fuzzy / ILIKE acceleration
CREATE EXTENSION IF NOT EXISTS citext;      -- case-insensitive text
CREATE EXTENSION IF NOT EXISTS unaccent;    -- accent-insensitive search

-- IMMUTABLE wrapper required to use unaccent() in generated columns / indexes.
-- Plain unaccent() is STABLE because it depends on the current dictionary.
CREATE OR REPLACE FUNCTION immutable_unaccent(text)
RETURNS text LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT AS $$
  SELECT public.unaccent('public.unaccent'::regdictionary, $1);
$$;

-- ----------------------------------------------------------------------------
-- Domain types — input validation at the DB layer
-- ----------------------------------------------------------------------------

-- Supported UI locales (ISO 639-1). Extend with: ALTER TYPE locale_code ADD VALUE 'it';
CREATE TYPE locale_code AS ENUM ('en', 'es', 'pt', 'fr', 'de', 'ar');

-- URL-safe slug: lowercase, digits, hyphens; 1..120 chars; no leading/trailing/double hyphens.
CREATE DOMAIN slug AS TEXT
  CHECK (VALUE ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND length(VALUE) BETWEEN 1 AND 120);

-- HTTPS URL or app-relative path. Rejects javascript:, data:, ftp:, etc.
CREATE DOMAIN https_url AS TEXT
  CHECK (VALUE ~* '^(https://|/)[^[:space:]]+$' AND length(VALUE) <= 2048);

-- ----------------------------------------------------------------------------
-- Helpers
-- ----------------------------------------------------------------------------

-- Map locale -> tsvector regconfig. Used by the search_vector generated column
-- and by the /search query helper. IMMUTABLE so it's usable in generated cols.
CREATE OR REPLACE FUNCTION fts_config_for(p_locale locale_code)
RETURNS regconfig LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
  SELECT CASE p_locale
    WHEN 'en' THEN 'pg_catalog.english'::regconfig
    WHEN 'es' THEN 'pg_catalog.spanish'::regconfig
    WHEN 'pt' THEN 'pg_catalog.portuguese'::regconfig
    WHEN 'fr' THEN 'pg_catalog.french'::regconfig
    WHEN 'de' THEN 'pg_catalog.german'::regconfig
    ELSE             'pg_catalog.simple'::regconfig   -- e.g. Arabic: no snowball
  END;
$$;

-- IMMUTABLE helper that builds the weighted tsvector. Inlined CASE so the
-- generated column resolves a literal regconfig per row (works on PG 12+).
CREATE OR REPLACE FUNCTION build_search_vector(
    p_locale       locale_code,
    p_title        TEXT,
    p_description  TEXT
) RETURNS tsvector LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
  SELECT
    setweight(to_tsvector(
        CASE p_locale
            WHEN 'en' THEN 'pg_catalog.english'::regconfig
            WHEN 'es' THEN 'pg_catalog.spanish'::regconfig
            WHEN 'pt' THEN 'pg_catalog.portuguese'::regconfig
            WHEN 'fr' THEN 'pg_catalog.french'::regconfig
            WHEN 'de' THEN 'pg_catalog.german'::regconfig
            ELSE             'pg_catalog.simple'::regconfig
        END,
        immutable_unaccent(coalesce(p_title, ''))
    ), 'A')
  ||setweight(to_tsvector(
        CASE p_locale
            WHEN 'en' THEN 'pg_catalog.english'::regconfig
            WHEN 'es' THEN 'pg_catalog.spanish'::regconfig
            WHEN 'pt' THEN 'pg_catalog.portuguese'::regconfig
            WHEN 'fr' THEN 'pg_catalog.french'::regconfig
            WHEN 'de' THEN 'pg_catalog.german'::regconfig
            ELSE             'pg_catalog.simple'::regconfig
        END,
        immutable_unaccent(coalesce(p_description, ''))
    ), 'B');
$$;

CREATE OR REPLACE FUNCTION touch_updated_at()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN NEW.updated_at := now(); RETURN NEW; END $$;

-- ============================================================================
-- Reference tables (normalized)
-- ============================================================================

CREATE TABLE categories (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug            slug        NOT NULL UNIQUE,
    default_locale  locale_code NOT NULL DEFAULT 'en',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_categories_touch
  BEFORE UPDATE ON categories
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TABLE category_translations (
    category_id BIGINT      NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    locale      locale_code NOT NULL,
    name        TEXT        NOT NULL CHECK (length(name) BETWEEN 1 AND 200),
    description TEXT        CHECK (description IS NULL OR length(description) <= 2000),
    PRIMARY KEY (category_id, locale)
);

CREATE TABLE subreddits (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name CITEXT NOT NULL UNIQUE
                CHECK (name ~ '^[A-Za-z0-9_]{1,21}$')   -- Reddit's real constraint
);

CREATE TABLE authors (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username CITEXT NOT NULL UNIQUE
                  CHECK (username IN ('[deleted]', '[removed]')
                      OR username ~ '^[A-Za-z0-9_-]{1,20}$')
);

CREATE TABLE tags (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug       CITEXT      NOT NULL UNIQUE
                           CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND length(slug) BETWEEN 1 AND 120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tag_translations (
    tag_id BIGINT      NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    locale locale_code NOT NULL,
    name   TEXT        NOT NULL CHECK (length(name) BETWEEN 1 AND 80),
    PRIMARY KEY (tag_id, locale)
);

-- ============================================================================
-- Core: memes
-- ============================================================================

CREATE TABLE memes (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id     BIGINT      NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    slug            slug        NOT NULL,
    subreddit_id    BIGINT      REFERENCES subreddits(id) ON DELETE SET NULL,
    author_id       BIGINT      REFERENCES authors(id)    ON DELETE SET NULL,
    default_locale  locale_code NOT NULL DEFAULT 'en',
    score           INTEGER     NOT NULL DEFAULT 0
                                CHECK (score BETWEEN -1000000 AND 100000000),
    source_url      https_url,
    post_url        https_url,
    created_at      TIMESTAMPTZ,                     -- original post time at the source
    indexed_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,                     -- soft delete
    UNIQUE (category_id, slug)                       -- replaces V1's TEXT composite PK
);
CREATE TRIGGER trg_memes_touch
  BEFORE UPDATE ON memes
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- One row per (meme, locale) — title/description/search_vector are translatable.
CREATE TABLE meme_translations (
    meme_id       BIGINT      NOT NULL REFERENCES memes(id) ON DELETE CASCADE,
    locale        locale_code NOT NULL,
    title         TEXT        NOT NULL CHECK (length(title) BETWEEN 1 AND 500),
    description   TEXT        CHECK (description IS NULL OR length(description) <= 10000),
    search_vector tsvector    GENERATED ALWAYS AS (
        build_search_vector(locale, title, description)
    ) STORED,
    PRIMARY KEY (meme_id, locale)
);

-- Multiple images per meme (V1 had a single image_path).
CREATE TABLE meme_images (
    id         BIGINT  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    meme_id    BIGINT  NOT NULL REFERENCES memes(id) ON DELETE CASCADE,
    path       TEXT    NOT NULL CHECK (length(path) BETWEEN 1 AND 2048),
    width      INTEGER CHECK (width  IS NULL OR width  > 0),
    height     INTEGER CHECK (height IS NULL OR height > 0),
    bytes      BIGINT  CHECK (bytes  IS NULL OR bytes  > 0),
    mime_type  TEXT    CHECK (mime_type IS NULL OR mime_type ~ '^[a-z]+/[a-z0-9.+-]+$'),
    position   INTEGER NOT NULL DEFAULT 0 CHECK (position >= 0),
    is_primary BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (meme_id, position)
);

-- At most one primary image per meme.
CREATE UNIQUE INDEX uq_meme_images_one_primary
  ON meme_images (meme_id) WHERE is_primary;

CREATE TABLE meme_tags (
    meme_id BIGINT NOT NULL REFERENCES memes(id) ON DELETE CASCADE,
    tag_id  BIGINT NOT NULL REFERENCES tags(id)  ON DELETE CASCADE,
    PRIMARY KEY (meme_id, tag_id)
);

-- ============================================================================
-- Indexes — covering the API's hot paths
-- ============================================================================

-- listMemes / listMemesByCategory (sort=score | created_at)
-- The trailing `id DESC` makes these usable for keyset pagination
-- (`WHERE (score, id) < ($lastScore, $lastId) ORDER BY score DESC, id DESC`).
CREATE INDEX idx_memes_cat_score
    ON memes (category_id, score DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_memes_cat_created
    ON memes (category_id, created_at DESC NULLS LAST, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_memes_subreddit_score
    ON memes (subreddit_id, score DESC, id DESC)
    WHERE deleted_at IS NULL AND subreddit_id IS NOT NULL;

CREATE INDEX idx_memes_score_global
    ON memes (score DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_memes_created_global
    ON memes (created_at DESC NULLS LAST, id DESC)
    WHERE deleted_at IS NULL;

-- sort=title (locale-aware) — supports ORDER BY title in the chosen locale.
CREATE INDEX idx_meme_translations_locale_title
    ON meme_translations (locale, title);

-- /search FTS (per locale, weighted A=title B=description, accent-folded)
CREATE INDEX idx_meme_translations_fts
    ON meme_translations USING gin (search_vector);

-- Trigram fallback: powers ILIKE '%foo%' and "did you mean" suggestions.
CREATE INDEX idx_meme_translations_title_trgm
    ON meme_translations USING gin (title gin_trgm_ops);

-- Tag-based filtering (data is here even if API doesn't expose it yet).
CREATE INDEX idx_meme_tags_tag ON meme_tags (tag_id, meme_id);

-- ============================================================================
-- Materialized aggregates — fast / and /categories
-- V1 recomputed COUNT(*) on every request via a regular VIEW.
-- ============================================================================

CREATE MATERIALIZED VIEW category_counts AS
SELECT
    c.id                       AS category_id,
    c.slug                     AS category,
    COUNT(m.id)                AS count,
    COALESCE(MAX(m.score), 0)  AS top_score,
    MAX(m.indexed_at)          AS last_indexed_at
FROM categories c
LEFT JOIN memes m
       ON m.category_id = c.id
      AND m.deleted_at IS NULL
GROUP BY c.id, c.slug;

CREATE UNIQUE INDEX uq_category_counts        ON category_counts (category_id);
CREATE INDEX        idx_category_counts_count ON category_counts (count DESC);

CREATE MATERIALIZED VIEW stats_snapshot AS
SELECT
    1 AS singleton,
    (SELECT COUNT(*)::BIGINT FROM memes      WHERE deleted_at IS NULL) AS total_memes,
    (SELECT COUNT(*)::BIGINT FROM categories)                          AS total_categories,
    (SELECT COUNT(*)::BIGINT FROM subreddits)                          AS total_subreddits,
    (SELECT category FROM category_counts ORDER BY count DESC, category ASC LIMIT 1)
                                                                       AS top_category,
    (SELECT MAX(indexed_at) FROM memes)                                AS indexed_at;

CREATE UNIQUE INDEX uq_stats_snapshot ON stats_snapshot (singleton);

-- /admin/reindex calls this after each upsert / full rescan.
-- CONCURRENTLY requires the unique indexes above and never blocks readers.
-- Falls back to non-concurrent refresh on first call (when the view has no data).
CREATE OR REPLACE FUNCTION refresh_stats() RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
  BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY category_counts;
  EXCEPTION WHEN feature_not_supported OR object_not_in_prerequisite_state THEN
    REFRESH MATERIALIZED VIEW category_counts;
  END;
  BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY stats_snapshot;
  EXCEPTION WHEN feature_not_supported OR object_not_in_prerequisite_state THEN
    REFRESH MATERIALIZED VIEW stats_snapshot;
  END;
END $$;

-- ============================================================================
-- Search helper — used by /search?q=...&locale=es
-- Returns total_count via window function so the API doesn't need a second
-- query for pagination metadata.
-- ============================================================================
CREATE OR REPLACE FUNCTION search_memes(
    p_query  TEXT,
    p_locale locale_code DEFAULT 'en',
    p_limit  INT  DEFAULT 20,
    p_offset INT  DEFAULT 0
) RETURNS TABLE (
    meme_id     BIGINT,
    slug        TEXT,
    category    TEXT,
    title       TEXT,
    description TEXT,
    score       INT,
    rank        REAL,
    total_count BIGINT
) LANGUAGE sql STABLE PARALLEL SAFE AS $$
    WITH q AS (
        SELECT websearch_to_tsquery(
                   fts_config_for(p_locale),
                   immutable_unaccent(p_query)
               ) AS tsq
    ),
    matches AS (
        SELECT
            m.id          AS meme_id,
            m.slug::text  AS slug,
            c.slug::text  AS category,
            mt.title,
            mt.description,
            m.score,
            ts_rank(mt.search_vector, q.tsq) AS rank
        FROM meme_translations mt
        JOIN q                  ON true
        JOIN memes      m       ON m.id = mt.meme_id AND m.deleted_at IS NULL
        JOIN categories c       ON c.id = m.category_id
        WHERE mt.locale = p_locale
          AND mt.search_vector @@ q.tsq
    )
    SELECT
        meme_id, slug, category, title, description, score, rank,
        count(*) OVER () AS total_count
    FROM   matches
    ORDER  BY rank DESC, score DESC, meme_id DESC
    LIMIT  p_limit
    OFFSET p_offset;
$$;
