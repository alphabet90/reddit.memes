package com.memes.api.repository;

import lombok.Builder;
import org.springframework.lang.Nullable;

@Builder
public record MemeImageRow(
    String path,
    @Nullable Integer width,
    @Nullable Integer height,
    @Nullable Long bytes,
    @Nullable String mimeType,
    int position,
    boolean isPrimary
) {}
