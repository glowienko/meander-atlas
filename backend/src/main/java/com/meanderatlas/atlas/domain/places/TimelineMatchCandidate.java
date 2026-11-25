package com.meanderatlas.atlas.domain.places;

import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.VisitStatus;
import org.jspecify.annotations.Nullable;

public record TimelineMatchCandidate(
        PlaceId id, @Nullable String googlePlaceId, VisitStatus visitStatus) {}
