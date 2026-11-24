package com.meanderatlas.ingestion.domain;

import com.meanderatlas.shared.GeoPoint;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record ParsedTimelineVisit(
        String sourceFile,
        @Nullable String googlePlaceId,
        GeoPoint coordinates,
        Instant startTime,
        Instant endTime,
        @Nullable String semanticType,
        @Nullable Double visitProbability,
        @Nullable Double candidateProbability) {

    private static final String NULL_SOURCE_FILE_MESSAGE = "sourceFile must not be null";
    private static final String NULL_COORDINATES_MESSAGE = "coordinates must not be null";
    private static final String NULL_START_TIME_MESSAGE = "startTime must not be null";
    private static final String NULL_END_TIME_MESSAGE = "endTime must not be null";

    public ParsedTimelineVisit {
        Objects.requireNonNull(sourceFile, NULL_SOURCE_FILE_MESSAGE);
        Objects.requireNonNull(coordinates, NULL_COORDINATES_MESSAGE);
        Objects.requireNonNull(startTime, NULL_START_TIME_MESSAGE);
        Objects.requireNonNull(endTime, NULL_END_TIME_MESSAGE);
    }
}
