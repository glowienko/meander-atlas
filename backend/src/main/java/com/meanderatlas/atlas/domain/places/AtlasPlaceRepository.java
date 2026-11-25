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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public interface AtlasPlaceRepository {

    List<StoredPlace> findPlaces(UserId userId, PlaceQueryFilter filter);

    Optional<PlaceVisitState> findVisitState(UserId userId, PlaceId placeId);

    List<PlaceVisitState> findVisitStates(UserId userId, List<PlaceId> placeIds);

    boolean existsPlaceForUser(UserId userId, PlaceId placeId);

    Set<PlaceId> findOwnedPlaceIds(UserId userId, List<PlaceId> placeIds);

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
            @Nullable PlaceCategory upgradedCategory,
            @Nullable String upgradedSubcategory,
            @Nullable CategorySource upgradedCategorySource,
            @Nullable String googlePlaceIdToBackfill,
            @Nullable Long googleCidToBackfill,
            @Nullable String fallbackCountryCode);

    Optional<TimelineMatchCandidate> findExactOrNearTimelineMatch(
            UserId userId,
            @Nullable String googlePlaceId,
            GeoPoint coordinates,
            double exactRadiusMeters);

    Optional<PlaceId> findUnvisitedTriageCandidate(
            UserId userId, GeoPoint coordinates, double minRadiusMeters, double maxRadiusMeters);

    void backfillGooglePlaceId(UserId userId, PlaceId placeId, String googlePlaceId);
}
