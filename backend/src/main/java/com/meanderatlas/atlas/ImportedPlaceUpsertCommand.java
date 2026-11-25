package com.meanderatlas.atlas;

import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record ImportedPlaceUpsertCommand(
        String canonicalName,
        GeoPoint coordinates,
        CoordSource coordSource,
        @Nullable String googlePlaceId,
        @Nullable Long googleCid,
        PlaceCategory category,
        @Nullable String subcategory,
        CategorySource categorySource,
        @Nullable String countryCode,
        @Nullable String cityName,
        String accountLabel,
        String listName,
        ListKind listKind,
        @Nullable String note,
        @Nullable String tags,
        @Nullable String sourceUrl,
        @Nullable Instant savedAt,
        @Nullable VisitStatus initialVisitStatus,
        @Nullable VisitSource initialVisitSource,
        @Nullable Instant observedAt) {

    public ImportedPlaceUpsertCommand {
        Objects.requireNonNull(canonicalName, "canonicalName must not be null");
        Objects.requireNonNull(coordinates, "coordinates must not be null");
        Objects.requireNonNull(coordSource, "coordSource must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(categorySource, "categorySource must not be null");
        Objects.requireNonNull(accountLabel, "accountLabel must not be null");
        Objects.requireNonNull(listName, "listName must not be null");
        Objects.requireNonNull(listKind, "listKind must not be null");
    }
}
