package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.AccountListFacetView;
import com.meanderatlas.atlas.AtlasPlaceFilterQuery;
import com.meanderatlas.atlas.AtlasPlaceView;
import com.meanderatlas.atlas.AtlasStatsView;
import com.meanderatlas.atlas.CategoryStatView;
import com.meanderatlas.atlas.PlaceMembershipView;
import com.meanderatlas.atlas.RegionFilterQuery;
import com.meanderatlas.atlas.RegionScope;
import com.meanderatlas.atlas.RegionSummaryView;
import com.meanderatlas.atlas.SubcategoryStatView;
import com.meanderatlas.atlas.VisitStateSnapshotView;
import com.meanderatlas.atlas.domain.PlaceVisitState;
import com.meanderatlas.atlas.domain.RegionLevel;
import com.meanderatlas.atlas.domain.places.BoundingBox;
import com.meanderatlas.atlas.domain.places.PlaceQueryFilter;
import com.meanderatlas.atlas.domain.places.StoredMembership;
import com.meanderatlas.atlas.domain.places.StoredPlace;
import com.meanderatlas.atlas.domain.stats.AtlasStats;
import com.meanderatlas.atlas.domain.stats.RegionSummary;
import com.meanderatlas.atlas.domain.stats.RegionSummaryFilter;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class AtlasViewMapper {

    private AtlasViewMapper() {}

    static PlaceQueryFilter toPlaceQueryFilter(AtlasPlaceFilterQuery query) {
        BoundingBox bbox =
                toBoundingBox(query.minLng(), query.minLat(), query.maxLng(), query.maxLat());
        return new PlaceQueryFilter(
                query.q(),
                query.visitStatus(),
                query.derivedRole(),
                query.category(),
                query.account(),
                query.list(),
                query.countryCode(),
                query.admin1Id(),
                bbox);
    }

    static RegionSummaryFilter toRegionSummaryFilter(RegionFilterQuery query) {
        return new RegionSummaryFilter(
                query.q(), query.derivedRole(), query.category(), query.account(), query.list());
    }

    static RegionLevel toRegionLevel(RegionScope scope) {
        return switch (scope) {
            case COUNTRY -> RegionLevel.COUNTRY;
            case ADMIN1 -> RegionLevel.ADMIN1;
        };
    }

    static RegionScope toRegionScope(RegionLevel level) {
        return switch (level) {
            case COUNTRY -> RegionScope.COUNTRY;
            case ADMIN1 -> RegionScope.ADMIN1;
            case CITY ->
                    throw new IllegalStateException(
                            "CITY scope is not exposed in v0.1 regions API");
        };
    }

    static AtlasPlaceView toPlaceView(StoredPlace place) {
        List<PlaceMembershipView> memberships =
                place.memberships().stream().map(AtlasViewMapper::toMembershipView).toList();
        return new AtlasPlaceView(
                place.id().value(),
                place.canonicalName(),
                place.coordinates().latitude(),
                place.coordinates().longitude(),
                place.category(),
                place.subcategory(),
                place.categorySource(),
                place.derivedRole(),
                place.visitStatus(),
                place.visitSource(),
                place.visitCount(),
                place.firstVisitedAt(),
                place.lastVisitedAt(),
                place.countryCode(),
                place.admin1Id(),
                place.admin1Code(),
                place.admin1Name(),
                place.cityName(),
                place.coordSource(),
                place.photoUrl(),
                memberships);
    }

    static RegionSummaryView toRegionSummaryView(RegionSummary summary) {
        return new RegionSummaryView(
                summary.id(),
                toRegionScope(summary.level()),
                summary.code(),
                summary.countryCode(),
                summary.name(),
                summary.totalPlaces(),
                summary.visitedPlaces(),
                summary.unvisitedPlaces(),
                summary.needsTriagePlaces(),
                summary.completionRatio(),
                summary.minLng(),
                summary.minLat(),
                summary.maxLng(),
                summary.maxLat());
    }

    static AtlasStatsView toStatsView(AtlasStats stats) {
        return new AtlasStatsView(
                stats.totalMappedPlaces(),
                stats.visitedPlaces(),
                stats.unvisitedPlaces(),
                stats.needsTriagePlaces(),
                stats.visitedCountries(),
                stats.totalSavedCountries(),
                stats.visitedAdmin1Regions(),
                stats.totalSavedAdmin1Regions(),
                stats.categories().stream()
                        .map(item ->
                                new CategoryStatView(
                                        item.category(), item.totalCount(), item.visitedCount()))
                        .toList(),
                stats.topSubcategories().stream()
                        .map(item ->
                                new SubcategoryStatView(
                                        item.subcategory(),
                                        item.category(),
                                        item.totalCount(),
                                        item.visitedCount()))
                        .toList(),
                stats.accountLists().stream()
                        .map(item ->
                                new AccountListFacetView(
                                        item.accountLabel(),
                                        item.listName(),
                                        item.listKind(),
                                        item.placeCount()))
                        .toList());
    }

    static VisitStateSnapshotView toVisitStateSnapshot(PlaceVisitState state) {
        return new VisitStateSnapshotView(
                state.placeId().value(),
                state.status(),
                state.source(),
                state.visitCount(),
                state.firstVisitedAt(),
                state.lastVisitedAt());
    }

    private static PlaceMembershipView toMembershipView(StoredMembership membership) {
        return new PlaceMembershipView(
                membership.accountLabel(),
                membership.listName(),
                membership.listKind(),
                membership.note(),
                membership.tags(),
                membership.sourceUrl(),
                membership.savedAt());
    }

    private static @Nullable BoundingBox toBoundingBox(
            @Nullable Double minLng,
            @Nullable Double minLat,
            @Nullable Double maxLng,
            @Nullable Double maxLat) {
        if (minLng == null && minLat == null && maxLng == null && maxLat == null) {
            return null;
        }
        if (minLng == null || minLat == null || maxLng == null || maxLat == null) {
            throw new IllegalArgumentException(
                    "All four bounding box parameters (minLng, minLat, maxLng, maxLat) are required together");
        }
        return new BoundingBox(minLng, minLat, maxLng, maxLat);
    }
}
