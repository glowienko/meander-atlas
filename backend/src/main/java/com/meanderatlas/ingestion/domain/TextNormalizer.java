package com.meanderatlas.ingestion.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

final class TextNormalizer {

    private static final char UTF8_BOM = '\uFEFF';
    private static final char NBSP = '\u00A0';
    private static final char NARROW_NBSP = '\u202F';
    private static final char SPACE = ' ';
    private static final char POLISH_L_LOWER = '\u0142';
    private static final char POLISH_L_UPPER = '\u0141';
    private static final char ASCII_L = 'l';
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");
    private static final String SINGLE_SPACE = " ";

    private TextNormalizer() {}

    static String stripBom(String raw) {
        if (!raw.isEmpty() && raw.charAt(0) == UTF8_BOM) {
            return raw.substring(1);
        }
        return raw;
    }

    static String normalizeSpaces(String raw) {
        String replaced = raw.replace(NBSP, SPACE).replace(NARROW_NBSP, SPACE);
        return MULTI_WHITESPACE.matcher(replaced.trim()).replaceAll(SINGLE_SPACE);
    }

    static String normalizeFolded(String raw) {
        String spaced = normalizeSpaces(raw);
        String polishSafe =
                spaced.replace(POLISH_L_LOWER, ASCII_L).replace(POLISH_L_UPPER, ASCII_L);
        String decomposed = Normalizer.normalize(polishSafe, Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }

    static @Nullable String trimToNull(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
