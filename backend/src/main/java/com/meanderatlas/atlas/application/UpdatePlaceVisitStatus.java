package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.PlaceVisitStateView;
import com.meanderatlas.atlas.UpdatePlaceVisitStatusRequest;
import com.meanderatlas.atlas.VisitStateSnapshotView;
import com.meanderatlas.atlas.domain.PlaceVisitState;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import com.meanderatlas.shared.VisitSource;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdatePlaceVisitStatus {

    private final AtlasPlaceRepository placeRepository;
    private final CurrentUserProvider currentUserProvider;

    public UpdatePlaceVisitStatus(
            AtlasPlaceRepository placeRepository, CurrentUserProvider currentUserProvider) {
        this.placeRepository = placeRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public PlaceVisitStateView execute(UUID rawPlaceId, UpdatePlaceVisitStatusRequest request) {
        Objects.requireNonNull(rawPlaceId, "placeId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        UserId userId = currentUserProvider.currentUserId();
        PlaceId placeId = new PlaceId(rawPlaceId);
        if (!placeRepository.existsPlaceForUser(userId, placeId)) {
            throw new NoSuchElementException("Place not found: " + rawPlaceId);
        }

        PlaceVisitState current =
                placeRepository
                        .findVisitState(userId, placeId)
                        .orElseGet(() -> PlaceVisitState.unvisited(placeId, userId));
        VisitStateSnapshotView previousSnapshot = AtlasViewMapper.toVisitStateSnapshot(current);
        PlaceVisitState updated =
                current.apply(request.status(), VisitSource.MANUAL_SINGLE, Instant.now());
        placeRepository.upsertVisitStates(List.of(updated));

        return new PlaceVisitStateView(
                updated.placeId().value(),
                updated.status(),
                updated.source(),
                updated.visitCount(),
                updated.firstVisitedAt(),
                updated.lastVisitedAt(),
                previousSnapshot);
    }
}
