package com.meanderatlas.ingestion.domain.takeout;

import com.meanderatlas.ingestion.domain.GoogleCid;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.ListKind;
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

    public ParsedSavedPlace {
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(coordinates, "coordinates must not be null");
        Objects.requireNonNull(listKind, "listKind must not be null");
    }
}
