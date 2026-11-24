package com.meanderatlas.ingestion.domain;

import com.meanderatlas.atlas.ListKind;
import com.meanderatlas.shared.GeoPoint;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record ParsedSavedPlace(
        String sourceFile,
        String name,
        GeoPoint coordinates,
        @Nullable String googleMapsUrl,
        @Nullable GoogleCid googleCid,
        @Nullable String countryCode,
        @Nullable String address,
        @Nullable Instant savedAt,
        @Nullable String comment,
        ListKind listKind) {

    private static final String NULL_SOURCE_FILE_MESSAGE = "sourceFile must not be null";
    private static final String NULL_NAME_MESSAGE = "name must not be null";
    private static final String NULL_COORDINATES_MESSAGE = "coordinates must not be null";
    private static final String NULL_LIST_KIND_MESSAGE = "listKind must not be null";

    public ParsedSavedPlace {
        Objects.requireNonNull(sourceFile, NULL_SOURCE_FILE_MESSAGE);
        Objects.requireNonNull(name, NULL_NAME_MESSAGE);
        Objects.requireNonNull(coordinates, NULL_COORDINATES_MESSAGE);
        Objects.requireNonNull(listKind, NULL_LIST_KIND_MESSAGE);
    }
}
