package com.meanderatlas.ingestion.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class TextNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");

    private TextNormalizer() {}

    public static String stripBom(String raw) {
        return !raw.isEmpty() && raw.charAt(0) == '\uFEFF' ? raw.substring(1) : raw;
    }

    public static String normalizeSpaces(String raw) {
        String replaced = raw.replace('\u00A0', ' ').replace('\u202F', ' ');
        return MULTI_WHITESPACE.matcher(replaced.trim()).replaceAll(" ");
    }

    public static String normalizeFolded(String raw) {
        String polishSafe = normalizeSpaces(raw).replace('\u0142', 'l').replace('\u0141', 'l');
        String decomposed = Normalizer.normalize(polishSafe, Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }

    public static @Nullable String trimToNull(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
