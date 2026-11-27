package com.meanderatlas.atlas.domain.places;

import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import org.jspecify.annotations.Nullable;

public record PlaceEnrichmentUpdate(
        PlaceId placeId,
        @Nullable String upgradedCanonicalName,
        @Nullable GeoPoint upgradedCoordinates,
        @Nullable String osmId,
        @Nullable PlaceCategory upgradedCategory,
        @Nullable String upgradedSubcategory,
        DerivedRole derivedRole,
        @Nullable String cityName,
        @Nullable String photoUrl,
        @Nullable String openingHoursJson) {}
