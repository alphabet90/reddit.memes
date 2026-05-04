package com.memes.api.config;

import com.memes.api.generated.model.LocaleCode;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Converts {@code ?locale=es} query strings into {@link LocaleCode} values.
 * <p>
 * Spring's default enum converter resolves by Enum.name(), so {@code "en"} is
 * not recognised — only {@code "EN"} would be. The OpenAPI generator emits
 * the enum with lowercase string values, so we register an explicit converter
 * that delegates to {@link LocaleCode#fromValue}.
 */
@Component
public class LocaleCodeConverter implements Converter<String, LocaleCode> {

    @Override
    @Nullable
    public LocaleCode convert(@Nullable String source) {
        if (source == null || source.isBlank()) return null;
        // BCP 47 tags like "es-ar" or "es_AR" — keep only the language subtag
        String lang = source.toLowerCase().split("[-_]")[0];
        return LocaleCode.fromValue(lang);
    }
}
