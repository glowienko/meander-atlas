package com.meanderatlas.atlas;

import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import org.jspecify.annotations.Nullable;

public record PlaceEnrichmentCommand(
        PlaceId placeId,
        @Nullable GeoPoint refinedCoordinates,
        @Nullable String osmId,
        @Nullable PlaceCategory category,
        @Nullable String subcategory,
        @Nullable String cityName,
        @Nullable String photoUrl,
        @Nullable String openingHoursJson,
        @Nullable String resolvedCanonicalName) {}
