package com.meanderatlas.atlas.domain.places;

import com.meanderatlas.atlas.domain.PlaceVisitState;
import com.meanderatlas.shared.PlaceId;
import org.jspecify.annotations.Nullable;

public record TimelineMatchCandidate(
        PlaceId id,
        @Nullable String googlePlaceId,
        PlaceVisitState visitState,
        MatchConfidence confidence) {

    public enum MatchConfidence {
        EXACT,
        TRIAGE
    }

    public boolean acceptsGooglePlaceIdBackfill() {
        return confidence == MatchConfidence.EXACT && googlePlaceId == null;
    }
}
