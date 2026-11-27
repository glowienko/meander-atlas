package com.meanderatlas.atlas.domain.places;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class PlaceNameGuard {

    private static final Pattern DECIMAL_COORDS =
            Pattern.compile("^[+-]?\\d{1,2}(?:\\.\\d+)?\\s*,\\s*[+-]?\\d{1,3}(?:\\.\\d+)?$");
    private static final Pattern DMS_COORDS =
            Pattern.compile(
                    "^\\d{1,2}\\s*°\\s*\\d{1,2}\\s*['′](?:\\s*\\d{1,2}(?:\\.\\d+)?\\s*[\"″]?)?\\s*[NSns][,\\s]+"
                            + "\\d{1,3}\\s*°\\s*\\d{1,2}\\s*['′](?:\\s*\\d{1,2}(?:\\.\\d+)?\\s*[\"″]?)?\\s*[EWew]$");

    private PlaceNameGuard() {}

    public static boolean isCoordinateOnlyName(@Nullable String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        return DECIMAL_COORDS.matcher(trimmed).matches() || DMS_COORDS.matcher(trimmed).matches();
    }
}
