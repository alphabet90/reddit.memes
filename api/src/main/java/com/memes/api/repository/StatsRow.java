package com.memes.api.repository;

import lombok.Builder;
import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;

@Builder
public record StatsRow(
    long totalMemes,
    long totalCategories,
    long totalSubreddits,
    @Nullable String topCategory,
    @Nullable OffsetDateTime indexedAt
) {}
