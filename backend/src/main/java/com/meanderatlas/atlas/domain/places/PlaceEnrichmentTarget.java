package com.meanderatlas.atlas.domain.places;

import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import org.jspecify.annotations.Nullable;

public record PlaceEnrichmentTarget(
        PlaceId placeId,
        String canonicalName,
        GeoPoint coordinates,
        CoordSource coordSource,
        PlaceCategory category,
        CategorySource categorySource,
        @Nullable String osmId,
        @Nullable String photoUrl,
        @Nullable String countryCode,
        @Nullable String cityName) {}
