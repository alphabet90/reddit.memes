-- V2__drop_v1.sql
-- Drops the V1 denormalized memes table and its category_counts view so V3 can
-- create the new normalized schema using the same names. Data is intentionally
-- discarded: the MDX corpus under memes/ is the source of truth and will be
-- re-indexed via POST /admin/reindex after deploy.

DROP VIEW IF EXISTS category_counts;
DROP TABLE IF EXISTS memes CASCADE;
