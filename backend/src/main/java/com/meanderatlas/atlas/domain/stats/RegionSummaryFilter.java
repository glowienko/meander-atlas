package com.meanderatlas.atlas.domain.stats;

import com.meanderatlas.atlas.domain.RegionCodeNormalizer;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.PlaceCategory;
import org.jspecify.annotations.Nullable;

public record RegionSummaryFilter(
        @Nullable String q,
        @Nullable DerivedRole derivedRole,
        @Nullable PlaceCategory category,
        @Nullable String account,
        @Nullable String list) {

    public RegionSummaryFilter {
        q = RegionCodeNormalizer.trimToNull(q);
        account = RegionCodeNormalizer.trimToNull(account);
        list = RegionCodeNormalizer.trimToNull(list);
    }

    public static RegionSummaryFilter empty() {
        return new RegionSummaryFilter(null, null, null, null, null);
    }
}
