package com.memes.api.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses {@code jsonb_agg(...)} payloads returned by PostgreSQL into typed row
 * records. Centralized so the row mapper logic stays declarative.
 */
@UtilityClass
@Slf4j
public class JsonAggregates {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<JsonNode>> NODE_LIST = new TypeReference<>() {};

    public List<MemeTranslationRow> parseTranslations(String json) {
        return parseList(json).stream()
            .map(n -> MemeTranslationRow.builder()
                .locale(text(n, "locale"))
                .title(text(n, "title"))
                .description(text(n, "description"))
                .build())
            .toList();
    }

    public List<MemeImageRow> parseImages(String json) {
        return parseList(json).stream()
            .map(n -> MemeImageRow.builder()
                .path(text(n, "path"))
                .width(intOrNull(n, "width"))
                .height(intOrNull(n, "height"))
                .bytes(longOrNull(n, "bytes"))
                .mimeType(text(n, "mime_type"))
                .position(Optional.ofNullable(intOrNull(n, "position")).orElse(0))
                .isPrimary(n.path("is_primary").asBoolean(false))
                .build())
            .toList();
    }

    private List<JsonNode> parseList(String json) {
        return Optional.ofNullable(json)
            .filter(s -> !s.isBlank())
            .map(JsonAggregates::readSafe)
            .orElseGet(ArrayList::new);
    }

    private List<JsonNode> readSafe(String json) {
        try {
            return MAPPER.readValue(json, NODE_LIST);
        } catch (Exception e) {
            log.warn("Could not parse jsonb_agg payload: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private Integer intOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v == null || v.isNull()) ? null : v.asInt();
    }

    private Long longOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v == null || v.isNull()) ? null : v.asLong();
    }
}
