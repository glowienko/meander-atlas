package com.meanderatlas.ingestion.domain.timeline;

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

    public ParsedTimelineVisit {
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        Objects.requireNonNull(coordinates, "coordinates must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(endTime, "endTime must not be null");
    }
}
