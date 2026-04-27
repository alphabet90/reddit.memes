package com.memes.api.repository;

import lombok.Builder;
import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Input shape for {@link MemeRepository#upsertMeme} — mirrors the V2 normalized
 * tables but identifies lookups by name/slug so the caller doesn't have to
 * pre-resolve foreign keys.
 */
@Builder
public record MemeUpsert(
    String slug,
    String categorySlug,
    String defaultLocale,
    @Nullable String subredditName,
    @Nullable String authorUsername,
    int score,
    @Nullable OffsetDateTime createdAt,
    @Nullable String sourceUrl,
    @Nullable String postUrl,
    List<MemeTranslationRow> translations,
    List<MemeImageRow> images,
    List<String> tagSlugs
) {}
