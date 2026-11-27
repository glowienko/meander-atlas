package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.BulkVisitStatusRequest;
import com.meanderatlas.atlas.BulkVisitStatusResultView;
import com.meanderatlas.atlas.VisitStateSnapshotView;
import com.meanderatlas.atlas.domain.PlaceVisitState;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import com.meanderatlas.shared.VisitSource;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BulkUpdatePlaceVisitStatus {

    private final AtlasPlaceRepository placeRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public BulkUpdatePlaceVisitStatus(
            AtlasPlaceRepository placeRepository,
            CurrentUserProvider currentUserProvider,
            Clock clock) {
        this.placeRepository = placeRepository;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Transactional
    public BulkVisitStatusResultView execute(BulkVisitStatusRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<PlaceId> placeIds = request.placeIds().stream().distinct().map(PlaceId::new).toList();
        UserId userId = currentUserProvider.currentUserId();
        placeRepository.requireAllOwned(userId, placeIds);

        Map<PlaceId, PlaceVisitState> existingByPlaceId =
                placeRepository.findVisitStates(userId, placeIds).stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        PlaceVisitState::placeId, Function.identity()));

        Instant now = clock.instant();
        List<StateTransition> changedTransitions =
                placeIds.stream()
                        .map(id ->
                                existingByPlaceId.getOrDefault(
                                        id, PlaceVisitState.unvisited(id, userId)))
                        .map(current ->
                                new StateTransition(
                                        current,
                                        current.apply(
                                                request.targetStatus(),
                                                VisitSource.MANUAL_BULK,
                                                now)))
                        .filter(StateTransition::changed)
                        .toList();

        List<PlaceVisitState> updatedStates =
                changedTransitions.stream().map(StateTransition::updated).toList();
        if (!updatedStates.isEmpty()) {
            placeRepository.upsertVisitStates(updatedStates);
        }
        List<VisitStateSnapshotView> previousStates =
                changedTransitions.stream()
                        .map(StateTransition::current)
                        .map(AtlasViewMapper::toVisitStateSnapshot)
                        .toList();
        return new BulkVisitStatusResultView(updatedStates.size(), previousStates);
    }

    private record StateTransition(PlaceVisitState current, PlaceVisitState updated) {
        boolean changed() {
            return !updated.equals(current);
        }
    }
}
