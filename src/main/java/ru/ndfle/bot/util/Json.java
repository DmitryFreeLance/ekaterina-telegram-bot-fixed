package ru.ndfle.bot.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class Json {
    private Json() {}

    public static final ObjectMapper MAPPER = new ObjectMapper();

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("JSON serialize failed", e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> typeRef, T fallback) {
        if (json == null || json.isBlank()) return fallback;
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (Exception e) {
            return fallback;
        }
    }
}
