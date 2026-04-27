package com.memes.api.repository;

import lombok.Builder;
import org.springframework.lang.Nullable;

@Builder
public record MemeTranslationRow(
    String locale,
    String title,
    @Nullable String description
) {}
