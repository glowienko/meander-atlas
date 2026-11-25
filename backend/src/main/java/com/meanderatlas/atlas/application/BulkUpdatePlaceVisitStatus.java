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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BulkUpdatePlaceVisitStatus {

    private final AtlasPlaceRepository placeRepository;
    private final CurrentUserProvider currentUserProvider;

    public BulkUpdatePlaceVisitStatus(
            AtlasPlaceRepository placeRepository, CurrentUserProvider currentUserProvider) {
        this.placeRepository = placeRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public BulkVisitStatusResultView execute(BulkVisitStatusRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<PlaceId> placeIds = request.placeIds().stream().distinct().map(PlaceId::new).toList();
        UserId userId = currentUserProvider.currentUserId();
        Set<PlaceId> ownedIds = placeRepository.findOwnedPlaceIds(userId, placeIds);
        if (ownedIds.size() != placeIds.size()) {
            throw new NoSuchElementException("One or more places were not found");
        }

        Map<PlaceId, PlaceVisitState> existingByPlaceId =
                placeRepository.findVisitStates(userId, placeIds).stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        PlaceVisitState::placeId, Function.identity()));

        List<PlaceVisitState> currentStates =
                placeIds.stream()
                        .map(id ->
                                existingByPlaceId.getOrDefault(
                                        id, PlaceVisitState.unvisited(id, userId)))
                        .toList();
        List<VisitStateSnapshotView> previousStates =
                currentStates.stream().map(AtlasViewMapper::toVisitStateSnapshot).toList();

        Instant now = Instant.now();
        List<PlaceVisitState> updatedStates =
                currentStates.stream()
                        .map(current ->
                                current.apply(request.targetStatus(), VisitSource.MANUAL_BULK, now))
                        .toList();
        placeRepository.upsertVisitStates(updatedStates);
        return new BulkVisitStatusResultView(updatedStates.size(), previousStates);
    }
}
