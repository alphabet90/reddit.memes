package com.memes.api.repository;

import lombok.Builder;
import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.List;

@Builder
public record MemeRow(
    long id,
    String slug,
    String categorySlug,
    String defaultLocale,
    @Nullable String subredditName,
    @Nullable String authorUsername,
    int score,
    @Nullable OffsetDateTime createdAt,
    @Nullable String sourceUrl,
    @Nullable String postUrl,
    @Nullable OffsetDateTime indexedAt,
    List<MemeTranslationRow> translations,
    List<MemeImageRow> images,
    List<String> tagSlugs
) {}
