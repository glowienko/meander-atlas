package com.meanderatlas.atlas.domain.places;

import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record StoredPlace(
        PlaceId id,
        String canonicalName,
        GeoPoint coordinates,
        PlaceCategory category,
        @Nullable String subcategory,
        CategorySource categorySource,
        DerivedRole derivedRole,
        VisitStatus visitStatus,
        @Nullable VisitSource visitSource,
        int visitCount,
        @Nullable Instant firstVisitedAt,
        @Nullable Instant lastVisitedAt,
        @Nullable String countryCode,
        @Nullable UUID admin1Id,
        @Nullable String admin1Code,
        @Nullable String admin1Name,
        @Nullable String cityName,
        CoordSource coordSource,
        @Nullable String photoUrl,
        List<StoredMembership> memberships) {

    public StoredPlace withMemberships(List<StoredMembership> resolvedMemberships) {
        return new StoredPlace(
                id,
                canonicalName,
                coordinates,
                category,
                subcategory,
                categorySource,
                derivedRole,
                visitStatus,
                visitSource,
                visitCount,
                firstVisitedAt,
                lastVisitedAt,
                countryCode,
                admin1Id,
                admin1Code,
                admin1Name,
                cityName,
                coordSource,
                photoUrl,
                resolvedMemberships);
    }
}
