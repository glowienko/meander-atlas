package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.domain.PlaceVisitState;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.atlas.domain.places.MatchedPlace;
import com.meanderatlas.atlas.domain.places.PlaceQueryFilter;
import com.meanderatlas.atlas.domain.places.StoredMembership;
import com.meanderatlas.atlas.domain.places.StoredPlace;
import com.meanderatlas.atlas.domain.places.TimelineMatchCandidate;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import com.meanderatlas.shared.VisitStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public final class FakeAtlasPlaceRepository implements AtlasPlaceRepository {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final Map<PlaceId, MutablePlaceRecord> places = new LinkedHashMap<>();
    private final Map<PlaceId, PlaceVisitState> visitStates = new HashMap<>();
    private final Map<PlaceId, List<StoredMembership>> memberships = new HashMap<>();

    public MutablePlaceRecord place(PlaceId id) {
        return Objects.requireNonNull(places.get(id));
    }

    public PlaceVisitState visitState(PlaceId id) {
        return Objects.requireNonNull(visitStates.get(id));
    }

    public List<StoredMembership> membershipsOf(PlaceId id) {
        return List.copyOf(memberships.getOrDefault(id, List.of()));
    }

    public int placeCount() {
        return places.size();
    }

    @Override
    public List<StoredPlace> findPlaces(UserId userId, PlaceQueryFilter filter) {
        return List.of();
    }

    @Override
    public Optional<PlaceVisitState> findVisitState(UserId userId, PlaceId placeId) {
        return Optional.ofNullable(visitStates.get(placeId));
    }

    @Override
    public List<PlaceVisitState> findVisitStates(UserId userId, List<PlaceId> placeIds) {
        return placeIds.stream().map(visitStates::get).filter(Objects::nonNull).toList();
    }

    @Override
    public boolean existsPlaceForUser(UserId userId, PlaceId placeId) {
        MutablePlaceRecord record = places.get(placeId);
        return record != null && record.userId.equals(userId);
    }

    @Override
    public Set<PlaceId> findOwnedPlaceIds(UserId userId, List<PlaceId> placeIds) {
        return placeIds.stream()
                .filter(id -> existsPlaceForUser(userId, id))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void upsertVisitStates(List<PlaceVisitState> states) {
        states.forEach(state -> visitStates.put(state.placeId(), state));
    }

    @Override
    public Optional<MatchedPlace> findCanonicalMatch(
            UserId userId,
            @Nullable String googlePlaceId,
            @Nullable Long googleCid,
            GeoPoint coordinates,
            String canonicalName,
            double radiusMeters,
            double minTrigramSimilarity) {
        if (googlePlaceId != null) {
            Optional<MatchedPlace> byPlaceId =
                    places.values().stream()
                            .filter(p -> p.userId.equals(userId))
                            .filter(p -> googlePlaceId.equals(p.googlePlaceId))
                            .map(MutablePlaceRecord::toMatchedPlace)
                            .findFirst();
            if (byPlaceId.isPresent()) {
                return byPlaceId;
            }
        }
        if (googleCid != null) {
            Optional<MatchedPlace> byCid = findByGoogleCid(userId, googleCid);
            if (byCid.isPresent()) {
                return byCid;
            }
        }
        return places.values().stream()
                .filter(p -> p.userId.equals(userId))
                .filter(p -> distanceMeters(p.coordinates, coordinates) <= radiusMeters)
                .filter(p -> p.canonicalName.equalsIgnoreCase(canonicalName))
                .map(MutablePlaceRecord::toMatchedPlace)
                .findFirst();
    }

    @Override
    public Optional<MatchedPlace> findByGoogleCid(UserId userId, long googleCid) {
        return places.values().stream()
                .filter(p -> p.userId.equals(userId))
                .filter(p -> Objects.equals(p.googleCid, googleCid))
                .map(MutablePlaceRecord::toMatchedPlace)
                .findFirst();
    }

    @Override
    public PlaceId insertImportedPlace(
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
            @Nullable String cityName) {
        if (coordSource == CoordSource.GOOGLE_TEMP) {
            throw new IllegalArgumentException(
                    "GOOGLE_TEMP coordinates require explicit expiration TTL");
        }
        PlaceId id = PlaceId.random();
        places.put(
                id,
                new MutablePlaceRecord(
                        id,
                        userId,
                        canonicalName,
                        coordinates,
                        googlePlaceId,
                        googleCid,
                        category,
                        subcategory,
                        categorySource,
                        derivedRole,
                        fallbackCountryCode));
        return id;
    }

    @Override
    public void upsertMembership(
            UserId userId,
            PlaceId placeId,
            String accountLabel,
            String listName,
            ListKind listKind,
            @Nullable String note,
            @Nullable String tags,
            @Nullable String sourceUrl,
            @Nullable Instant savedAt) {
        List<StoredMembership> current =
                memberships.computeIfAbsent(placeId, _ -> new ArrayList<>());
        current.removeIf(
                m -> m.accountLabel().equals(accountLabel) && m.listName().equals(listName));
        current.add(
                new StoredMembership(
                        placeId, accountLabel, listName, listKind, note, tags, sourceUrl, savedAt));
    }

    @Override
    public List<ListKind> findMembershipListKinds(PlaceId placeId) {
        return memberships.getOrDefault(placeId, List.of()).stream()
                .map(StoredMembership::listKind)
                .distinct()
                .toList();
    }

    @Override
    public void updatePlaceAfterMerge(
            UserId userId,
            PlaceId placeId,
            DerivedRole derivedRole,
            @Nullable PlaceCategory upgradedCategory,
            @Nullable String upgradedSubcategory,
            @Nullable CategorySource upgradedCategorySource,
            @Nullable String googlePlaceIdToBackfill,
            @Nullable Long googleCidToBackfill,
            @Nullable String fallbackCountryCode) {
        MutablePlaceRecord record = place(placeId);
        record.derivedRole = derivedRole;
        if (upgradedCategory != null) {
            record.category = upgradedCategory;
            record.subcategory = upgradedSubcategory;
            if (upgradedCategorySource != null) {
                record.categorySource = upgradedCategorySource;
            }
        }
        if (record.googlePlaceId == null && googlePlaceIdToBackfill != null) {
            record.googlePlaceId = googlePlaceIdToBackfill;
        }
        if (record.googleCid == null && googleCidToBackfill != null) {
            record.googleCid = googleCidToBackfill;
        }
        if (record.countryCode == null && fallbackCountryCode != null) {
            record.countryCode = fallbackCountryCode;
        }
    }

    @Override
    public Optional<TimelineMatchCandidate> findExactOrNearTimelineMatch(
            UserId userId,
            @Nullable String googlePlaceId,
            GeoPoint coordinates,
            double exactRadiusMeters) {
        if (googlePlaceId != null) {
            Optional<TimelineMatchCandidate> byPlaceId =
                    places.values().stream()
                            .filter(p -> p.userId.equals(userId))
                            .filter(p -> googlePlaceId.equals(p.googlePlaceId))
                            .map(this::toTimelineCandidate)
                            .findFirst();
            if (byPlaceId.isPresent()) {
                return byPlaceId;
            }
        }
        return places.values().stream()
                .filter(p -> p.userId.equals(userId))
                .filter(p -> distanceMeters(p.coordinates, coordinates) <= exactRadiusMeters)
                .sorted((a, b) ->
                        Double.compare(
                                distanceMeters(a.coordinates, coordinates),
                                distanceMeters(b.coordinates, coordinates)))
                .map(this::toTimelineCandidate)
                .findFirst();
    }

    @Override
    public Optional<PlaceId> findUnvisitedTriageCandidate(
            UserId userId, GeoPoint coordinates, double minRadiusMeters, double maxRadiusMeters) {
        return places.values().stream()
                .filter(p -> p.userId.equals(userId))
                .filter(p -> currentStatus(p.id) == VisitStatus.UNVISITED)
                .filter(p -> {
                    double d = distanceMeters(p.coordinates, coordinates);
                    return d >= minRadiusMeters && d <= maxRadiusMeters;
                })
                .sorted((a, b) ->
                        Double.compare(
                                distanceMeters(a.coordinates, coordinates),
                                distanceMeters(b.coordinates, coordinates)))
                .map(p -> p.id)
                .findFirst();
    }

    @Override
    public void backfillGooglePlaceId(UserId userId, PlaceId placeId, String googlePlaceId) {
        MutablePlaceRecord record = places.get(placeId);
        if (record != null && record.userId.equals(userId) && record.googlePlaceId == null) {
            record.googlePlaceId = googlePlaceId;
        }
    }

    private TimelineMatchCandidate toTimelineCandidate(MutablePlaceRecord record) {
        return new TimelineMatchCandidate(
                record.id, record.googlePlaceId, currentStatus(record.id));
    }

    private VisitStatus currentStatus(PlaceId id) {
        PlaceVisitState state = visitStates.get(id);
        return state == null ? VisitStatus.UNVISITED : state.status();
    }

    private static double distanceMeters(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.longitude() - a.longitude());
        double h =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(h));
    }

    public static final class MutablePlaceRecord {
        public final PlaceId id;
        public final UserId userId;
        public final String canonicalName;
        public final GeoPoint coordinates;
        public @Nullable String googlePlaceId;
        public @Nullable Long googleCid;
        public PlaceCategory category;
        public @Nullable String subcategory;
        public CategorySource categorySource;
        public DerivedRole derivedRole;
        public @Nullable String countryCode;

        private MutablePlaceRecord(
                PlaceId id,
                UserId userId,
                String canonicalName,
                GeoPoint coordinates,
                @Nullable String googlePlaceId,
                @Nullable Long googleCid,
                PlaceCategory category,
                @Nullable String subcategory,
                CategorySource categorySource,
                DerivedRole derivedRole,
                @Nullable String countryCode) {
            this.id = id;
            this.userId = userId;
            this.canonicalName = canonicalName;
            this.coordinates = coordinates;
            this.googlePlaceId = googlePlaceId;
            this.googleCid = googleCid;
            this.category = category;
            this.subcategory = subcategory;
            this.categorySource = categorySource;
            this.derivedRole = derivedRole;
            this.countryCode = countryCode;
        }

        private MatchedPlace toMatchedPlace() {
            return new MatchedPlace(
                    id,
                    category,
                    categorySource,
                    googlePlaceId,
                    googleCid,
                    countryCode,
                    (UUID) null);
        }
    }
}
