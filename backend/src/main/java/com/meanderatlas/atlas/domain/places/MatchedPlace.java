package com.meanderatlas.atlas.domain.places;

import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record MatchedPlace(
        PlaceId id,
        PlaceCategory category,
        CategorySource categorySource,
        @Nullable String googlePlaceId,
        @Nullable Long googleCid,
        @Nullable String countryCode,
        @Nullable UUID admin1Id) {}
