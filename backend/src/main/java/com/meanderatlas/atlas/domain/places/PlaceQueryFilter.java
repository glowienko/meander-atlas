package com.meanderatlas.atlas.domain.places;

import com.meanderatlas.atlas.domain.RegionCodeNormalizer;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.VisitStatus;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record PlaceQueryFilter(
        @Nullable String q,
        @Nullable VisitStatus visitStatus,
        @Nullable DerivedRole derivedRole,
        @Nullable PlaceCategory category,
        @Nullable String account,
        @Nullable String list,
        @Nullable String countryCode,
        @Nullable UUID admin1Id,
        @Nullable BoundingBox bbox) {

    public PlaceQueryFilter {
        q = RegionCodeNormalizer.trimToNull(q);
        account = RegionCodeNormalizer.trimToNull(account);
        list = RegionCodeNormalizer.trimToNull(list);
        countryCode = RegionCodeNormalizer.normalizeCountryCode(countryCode);
    }

    public static PlaceQueryFilter forCountry(String countryCode) {
        return new PlaceQueryFilter(null, null, null, null, null, null, countryCode, null, null);
    }

    public static PlaceQueryFilter forAdmin1(UUID admin1Id) {
        return new PlaceQueryFilter(null, null, null, null, null, null, null, admin1Id, null);
    }
}
