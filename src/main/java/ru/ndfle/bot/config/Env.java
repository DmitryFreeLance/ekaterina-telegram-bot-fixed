package ru.ndfle.bot.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class Env {
    private Env() {}

    public static String require(String key) {
        String v = System.getenv(key);
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalStateException("Missing required env var: " + key);
        }
        return v.trim();
    }

    public static String optional(String key, String def) {
        String v = System.getenv(key);
        if (v == null || v.trim().isEmpty()) return def;
        return v.trim();
    }

    public static Set<Long> parseAdminIds(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new LinkedHashSet<>();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
