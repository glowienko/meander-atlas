package com.meanderatlas.atlas.domain.places;

import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.VisitStatus;
import java.util.Locale;
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
        q = trimToNull(q);
        account = trimToNull(account);
        list = trimToNull(list);
        countryCode = upperTrimToNull(countryCode);
    }

    public static PlaceQueryFilter empty() {
        return new PlaceQueryFilter(null, null, null, null, null, null, null, null, null);
    }

    public static PlaceQueryFilter forCountry(String countryCode) {
        return new PlaceQueryFilter(null, null, null, null, null, null, countryCode, null, null);
    }

    public static PlaceQueryFilter forAdmin1(UUID admin1Id) {
        return new PlaceQueryFilter(null, null, null, null, null, null, null, admin1Id, null);
    }

    private static @Nullable String trimToNull(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static @Nullable String upperTrimToNull(@Nullable String raw) {
        String trimmed = trimToNull(raw);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
