package com.meanderatlas.atlas.domain;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

public final class RegionCodeNormalizer {

    private RegionCodeNormalizer() {}

    public static @Nullable String normalizeCountryCode(@Nullable String raw) {
        String trimmed = trimToNull(raw);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    public static @Nullable String trimToNull(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
