package com.meanderatlas.atlas.domain.places;

import com.meanderatlas.atlas.domain.PlaceVisitState;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public interface AtlasPlaceRepository {

    List<StoredPlace> findPlaces(UserId userId, PlaceQueryFilter filter);

    Optional<PlaceVisitState> findVisitState(UserId userId, PlaceId placeId);

    List<PlaceVisitState> findVisitStates(UserId userId, List<PlaceId> placeIds);

    boolean existsPlaceForUser(UserId userId, PlaceId placeId);

    Set<PlaceId> findOwnedPlaceIds(UserId userId, List<PlaceId> placeIds);

    default void requireOwned(UserId userId, PlaceId placeId) {
        if (!existsPlaceForUser(userId, placeId)) {
            throw new NoSuchElementException("Place not found: " + placeId.value());
        }
    }

    default void requireAllOwned(UserId userId, Collection<PlaceId> placeIds) {
        Set<PlaceId> distinctIds = Set.copyOf(placeIds);
        if (distinctIds.isEmpty()) {
            return;
        }
        Set<PlaceId> ownedIds = findOwnedPlaceIds(userId, List.copyOf(distinctIds));
        if (ownedIds.size() != distinctIds.size()) {
            throw new NoSuchElementException("One or more places were not found");
        }
    }

    void upsertVisitStates(List<PlaceVisitState> states);

    Optional<MatchedPlace> findCanonicalMatch(
            UserId userId,
            @Nullable String googlePlaceId,
            @Nullable Long googleCid,
            GeoPoint coordinates,
            String canonicalName,
            double radiusMeters,
            double minTrigramSimilarity);

    Optional<MatchedPlace> findByGoogleCid(UserId userId, long googleCid);

    PlaceId insertImportedPlace(
            UserId userId,
            String canonicalName,
            GeoPoint coordinates,
            CoordSource coordSource,
            @Nullable String googlePlaceId,
            @Nullable Long googleCid,
            PlaceCategory category,
            @Nullable String subcategory,
            CategorySource categorySource,
            DerivedRole derivedRole,
            @Nullable String fallbackCountryCode,
            @Nullable String cityName);

    void upsertMembership(
            UserId userId,
            PlaceId placeId,
            String accountLabel,
            String listName,
            ListKind listKind,
            @Nullable String note,
            @Nullable String tags,
            @Nullable String sourceUrl,
            @Nullable Instant savedAt);

    List<ListKind> findMembershipListKinds(PlaceId placeId);

    void updatePlaceAfterMerge(
            UserId userId,
            PlaceId placeId,
            DerivedRole derivedRole,
            @Nullable GeoPoint upgradedCoordinates,
            @Nullable CoordSource upgradedCoordSource,
            @Nullable PlaceCategory upgradedCategory,
            @Nullable String upgradedSubcategory,
            @Nullable CategorySource upgradedCategorySource,
            @Nullable String googlePlaceIdToBackfill,
            @Nullable Long googleCidToBackfill,
            @Nullable String fallbackCountryCode);

    List<PlaceId> findPlaceIdsNeedingEnrichment(UserId userId);

    Optional<TimelineMatchCandidate> findTimelineMatchCandidate(
            UserId userId,
            @Nullable String googlePlaceId,
            GeoPoint coordinates,
            double exactRadiusMeters,
            double triageMaxRadiusMeters);

    void backfillGooglePlaceId(UserId userId, PlaceId placeId, String googlePlaceId);
}
