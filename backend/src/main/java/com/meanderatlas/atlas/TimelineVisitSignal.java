package com.meanderatlas.atlas;

import com.meanderatlas.shared.GeoPoint;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record TimelineVisitSignal(
        @Nullable String googlePlaceId, GeoPoint coordinates, Instant observedAt) {

    public TimelineVisitSignal {
        Objects.requireNonNull(coordinates, "coordinates must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
    }
}
