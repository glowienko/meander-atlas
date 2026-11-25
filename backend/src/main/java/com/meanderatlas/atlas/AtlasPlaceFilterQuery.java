package com.meanderatlas.atlas;

import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.VisitStatus;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record AtlasPlaceFilterQuery(
        @Nullable String q,
        @Nullable VisitStatus visitStatus,
        @Nullable DerivedRole derivedRole,
        @Nullable PlaceCategory category,
        @Nullable String account,
        @Nullable String list,
        @Nullable String countryCode,
        @Nullable UUID admin1Id,
        @Nullable Double minLng,
        @Nullable Double minLat,
        @Nullable Double maxLng,
        @Nullable Double maxLat) {

    public static AtlasPlaceFilterQuery empty() {
        return new AtlasPlaceFilterQuery(
                null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
