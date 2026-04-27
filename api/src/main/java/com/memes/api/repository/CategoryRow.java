package com.memes.api.repository;

import lombok.Builder;

import java.util.List;

@Builder
public record CategoryRow(
    long id,
    String slug,
    int count,
    int topScore,
    List<CategoryTranslationRow> translations
) {}
