package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.RestoreVisitStatesRequest;
import com.meanderatlas.atlas.RestoreVisitStatesResultView;
import com.meanderatlas.atlas.VisitStateSnapshotView;
import com.meanderatlas.atlas.domain.PlaceVisitState;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
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
public class RestorePlaceVisitStates {

    private final AtlasPlaceRepository placeRepository;
    private final CurrentUserProvider currentUserProvider;

    public RestorePlaceVisitStates(
            AtlasPlaceRepository placeRepository, CurrentUserProvider currentUserProvider) {
        this.placeRepository = placeRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public RestoreVisitStatesResultView execute(RestoreVisitStatesRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        UserId userId = currentUserProvider.currentUserId();
        Map<PlaceId, VisitStateSnapshotView> snapshotsById =
                request.snapshots().stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        snapshot -> new PlaceId(snapshot.placeId()),
                                        Function.identity(),
                                        (first, second) -> {
                                            throw new IllegalArgumentException(
                                                    "Duplicate placeId in restore snapshots: "
                                                            + first.placeId());
                                        }));
        List<PlaceId> placeIds =
                request.snapshots().stream()
                        .map(snapshot -> new PlaceId(snapshot.placeId()))
                        .toList();
        Set<PlaceId> ownedIds = placeRepository.findOwnedPlaceIds(userId, placeIds);
        if (ownedIds.size() != placeIds.size()) {
            throw new NoSuchElementException("One or more places were not found");
        }

        Map<PlaceId, PlaceVisitState> currentByPlaceId =
                placeRepository.findVisitStates(userId, placeIds).stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        PlaceVisitState::placeId, Function.identity()));

        List<PlaceVisitState> restoredStates =
                placeIds.stream()
                        .map(id -> {
                            VisitStateSnapshotView snapshot =
                                    Objects.requireNonNull(snapshotsById.get(id));
                            PlaceVisitState target =
                                    new PlaceVisitState(
                                            id,
                                            userId,
                                            snapshot.status(),
                                            snapshot.source(),
                                            snapshot.visitCount(),
                                            snapshot.firstVisitedAt(),
                                            snapshot.lastVisitedAt());
                            PlaceVisitState current =
                                    currentByPlaceId.getOrDefault(
                                            id, PlaceVisitState.unvisited(id, userId));
                            return current.restoreFrom(target);
                        })
                        .toList();
        placeRepository.upsertVisitStates(restoredStates);
        return new RestoreVisitStatesResultView(restoredStates.size());
    }
}
