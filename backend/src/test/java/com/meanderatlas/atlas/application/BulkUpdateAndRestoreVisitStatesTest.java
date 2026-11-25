package com.meanderatlas.atlas.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meanderatlas.atlas.BulkVisitStatusRequest;
import com.meanderatlas.atlas.BulkVisitStatusResultView;
import com.meanderatlas.atlas.PlaceVisitStateView;
import com.meanderatlas.atlas.RestoreVisitStatesRequest;
import com.meanderatlas.atlas.RestoreVisitStatesResultView;
import com.meanderatlas.atlas.UpdatePlaceVisitStatusRequest;
import com.meanderatlas.atlas.VisitStateSnapshotView;
import com.meanderatlas.atlas.domain.PlaceVisitState;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.atlas.domain.places.MatchedPlace;
import com.meanderatlas.atlas.domain.places.PlaceQueryFilter;
import com.meanderatlas.atlas.domain.places.StoredPlace;
import com.meanderatlas.atlas.domain.places.TimelineMatchCandidate;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class BulkUpdateAndRestoreVisitStatesTest {

    private static final UserId USER_ID =
            new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    void bulkUpdatesPlacesCapturesPreviousStatesAndRestoresAtomically() {
        UUID unvisitedPlaceId = UUID.randomUUID();
        UUID timelinePlaceId = UUID.randomUUID();
        Instant timelineInstant = Instant.parse("2026-08-10T09:00:00Z");

        FakeAtlasPlaceRepository repo = new FakeAtlasPlaceRepository();
        repo.ownedIds.add(new PlaceId(unvisitedPlaceId));
        repo.ownedIds.add(new PlaceId(timelinePlaceId));
        repo.states.put(
                new PlaceId(timelinePlaceId),
                new PlaceVisitState(
                        new PlaceId(timelinePlaceId),
                        USER_ID,
                        VisitStatus.VISITED,
                        VisitSource.TIMELINE,
                        2,
                        timelineInstant,
                        timelineInstant));

        CurrentUserProvider userProvider = () -> USER_ID;
        BulkUpdatePlaceVisitStatus bulkUpdate = new BulkUpdatePlaceVisitStatus(repo, userProvider);
        RestorePlaceVisitStates restore = new RestorePlaceVisitStates(repo, userProvider);

        BulkVisitStatusResultView updated =
                bulkUpdate.execute(
                        new BulkVisitStatusRequest(
                                List.of(unvisitedPlaceId, timelinePlaceId), VisitStatus.VISITED));

        assertThat(updated.updatedCount()).isEqualTo(2);
        assertThat(updated.previousStates()).hasSize(2);
        assertThat(repo.stateOf(unvisitedPlaceId).status()).isEqualTo(VisitStatus.VISITED);
        assertThat(repo.stateOf(unvisitedPlaceId).source()).isEqualTo(VisitSource.MANUAL_BULK);
        assertThat(repo.stateOf(timelinePlaceId).source()).isEqualTo(VisitSource.MANUAL_BULK);
        assertThat(repo.stateOf(timelinePlaceId).visitCount()).isEqualTo(2);

        RestoreVisitStatesResultView restored =
                restore.execute(new RestoreVisitStatesRequest(updated.previousStates()));

        assertThat(restored.restoredCount()).isEqualTo(2);
        PlaceVisitState restoredUnvisited = repo.stateOf(unvisitedPlaceId);
        assertThat(restoredUnvisited.status()).isEqualTo(VisitStatus.UNVISITED);
        assertThat(restoredUnvisited.source()).isNull();
        assertThat(restoredUnvisited.visitCount()).isZero();

        PlaceVisitState restoredTimeline = repo.stateOf(timelinePlaceId);
        assertThat(restoredTimeline.status()).isEqualTo(VisitStatus.VISITED);
        assertThat(restoredTimeline.source()).isEqualTo(VisitSource.TIMELINE);
        assertThat(restoredTimeline.visitCount()).isEqualTo(2);
        assertThat(restoredTimeline.firstVisitedAt()).isEqualTo(timelineInstant);
    }

    @Test
    void rejectsUnknownOrUnownedPlaceIdsOnSingleAndBulkUpdates() {
        UUID ownedId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();

        FakeAtlasPlaceRepository repo = new FakeAtlasPlaceRepository();
        repo.ownedIds.add(new PlaceId(ownedId));
        CurrentUserProvider userProvider = () -> USER_ID;

        UpdatePlaceVisitStatus singleUpdate = new UpdatePlaceVisitStatus(repo, userProvider);
        BulkUpdatePlaceVisitStatus bulkUpdate = new BulkUpdatePlaceVisitStatus(repo, userProvider);

        assertThatThrownBy(() ->
                        singleUpdate.execute(
                                missingId, new UpdatePlaceVisitStatusRequest(VisitStatus.VISITED)))
                .isInstanceOf(NoSuchElementException.class);

        assertThatThrownBy(() ->
                        bulkUpdate.execute(
                                new BulkVisitStatusRequest(
                                        List.of(ownedId, missingId), VisitStatus.VISITED)))
                .isInstanceOf(NoSuchElementException.class);

        RestorePlaceVisitStates restore = new RestorePlaceVisitStates(repo, userProvider);
        VisitStateSnapshotView snapshotA =
                new VisitStateSnapshotView(ownedId, VisitStatus.UNVISITED, null, 0, null, null);
        VisitStateSnapshotView missingSnapshot =
                new VisitStateSnapshotView(missingId, VisitStatus.UNVISITED, null, 0, null, null);
        assertThatThrownBy(() ->
                        restore.execute(
                                new RestoreVisitStatesRequest(List.of(snapshotA, snapshotA))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        restore.execute(new RestoreVisitStatesRequest(List.of(missingSnapshot))))
                .isInstanceOf(NoSuchElementException.class);

        PlaceVisitStateView view =
                singleUpdate.execute(
                        ownedId, new UpdatePlaceVisitStatusRequest(VisitStatus.VISITED));
        assertThat(view.status()).isEqualTo(VisitStatus.VISITED);
        assertThat(view.source()).isEqualTo(VisitSource.MANUAL_SINGLE);
        assertThat(view.previousState().status()).isEqualTo(VisitStatus.UNVISITED);
    }

    private static final class FakeAtlasPlaceRepository implements AtlasPlaceRepository {

        private final Set<PlaceId> ownedIds = new java.util.HashSet<>();
        private final Map<PlaceId, PlaceVisitState> states = new HashMap<>();

        PlaceVisitState stateOf(UUID id) {
            return Objects.requireNonNull(states.get(new PlaceId(id)));
        }

        @Override
        public List<StoredPlace> findPlaces(UserId userId, PlaceQueryFilter filter) {
            return List.of();
        }

        @Override
        public Optional<PlaceVisitState> findVisitState(UserId userId, PlaceId placeId) {
            return Optional.ofNullable(states.get(placeId));
        }

        @Override
        public List<PlaceVisitState> findVisitStates(UserId userId, List<PlaceId> placeIds) {
            return placeIds.stream().map(states::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public boolean existsPlaceForUser(UserId userId, PlaceId placeId) {
            return ownedIds.contains(placeId);
        }

        @Override
        public Set<PlaceId> findOwnedPlaceIds(UserId userId, List<PlaceId> placeIds) {
            return placeIds.stream().filter(ownedIds::contains).collect(Collectors.toSet());
        }

        @Override
        public void upsertVisitStates(List<PlaceVisitState> updatedStates) {
            updatedStates.forEach(state -> states.put(state.placeId(), state));
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
            return Optional.empty();
        }

        @Override
        public Optional<MatchedPlace> findByGoogleCid(UserId userId, long googleCid) {
            return Optional.empty();
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
            return new PlaceId(UUID.randomUUID());
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
                @Nullable Instant savedAt) {}

        @Override
        public List<ListKind> findMembershipListKinds(PlaceId placeId) {
            return List.of();
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
                @Nullable String fallbackCountryCode) {}

        @Override
        public Optional<TimelineMatchCandidate> findExactOrNearTimelineMatch(
                UserId userId,
                @Nullable String googlePlaceId,
                GeoPoint coordinates,
                double exactRadiusMeters) {
            return Optional.empty();
        }

        @Override
        public Optional<PlaceId> findUnvisitedTriageCandidate(
                UserId userId,
                GeoPoint coordinates,
                double minRadiusMeters,
                double maxRadiusMeters) {
            return Optional.empty();
        }

        @Override
        public void backfillGooglePlaceId(UserId userId, PlaceId placeId, String googlePlaceId) {}
    }
}
