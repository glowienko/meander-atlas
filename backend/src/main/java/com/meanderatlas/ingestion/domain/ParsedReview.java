package com.meanderatlas.ingestion.domain;

import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.VisitSource;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record ParsedReview(
        String sourceFile,
        @Nullable String name,
        @Nullable GeoPoint coordinates,
        @Nullable String googleMapsUrl,
        @Nullable GoogleCid googleCid,
        @Nullable String countryCode,
        @Nullable String address,
        @Nullable Instant reviewedAt,
        @Nullable Integer starRating,
        @Nullable String reviewText,
        VisitSource visitSource) {

    private static final int MIN_STAR_RATING = 1;
    private static final int MAX_STAR_RATING = 5;
    private static final String NULL_SOURCE_FILE_MESSAGE = "sourceFile must not be null";
    private static final String NULL_VISIT_SOURCE_MESSAGE = "visitSource must not be null";
    private static final String INVALID_RATING_MESSAGE = "starRating must be between 1 and 5";

    public ParsedReview {
        Objects.requireNonNull(sourceFile, NULL_SOURCE_FILE_MESSAGE);
        Objects.requireNonNull(visitSource, NULL_VISIT_SOURCE_MESSAGE);
        if (starRating != null && (starRating < MIN_STAR_RATING || starRating > MAX_STAR_RATING)) {
            throw new IllegalArgumentException(INVALID_RATING_MESSAGE);
        }
    }
}
