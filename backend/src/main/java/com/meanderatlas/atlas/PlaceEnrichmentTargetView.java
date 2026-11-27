package com.meanderatlas.atlas;

import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import org.jspecify.annotations.Nullable;

public record PlaceEnrichmentTargetView(
        PlaceId placeId,
        String canonicalName,
        double lat,
        double lng,
        CoordSource coordSource,
        PlaceCategory category,
        CategorySource categorySource,
        @Nullable String osmId,
        @Nullable String photoUrl,
        @Nullable String countryCode,
        @Nullable String cityName) {}
