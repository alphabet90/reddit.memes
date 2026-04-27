package com.memes.api.repository;

import lombok.Builder;
import org.springframework.lang.Nullable;

@Builder
public record CategoryTranslationRow(
    String locale,
    String name,
    @Nullable String description
) {}
