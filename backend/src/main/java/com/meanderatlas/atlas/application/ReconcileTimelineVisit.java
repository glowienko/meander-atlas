package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.TimelineReconciliationOutcome;
import com.meanderatlas.atlas.TimelineVisitSignal;
import com.meanderatlas.atlas.domain.PlaceVisitState;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.atlas.domain.places.TimelineMatchCandidate;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconcileTimelineVisit {

    private static final double EXACT_MATCH_RADIUS_METERS = 50.0;
    private static final double TRIAGE_MAX_RADIUS_METERS = 150.0;

    private final AtlasPlaceRepository placeRepository;
    private final CurrentUserProvider currentUserProvider;

    public ReconcileTimelineVisit(
            AtlasPlaceRepository placeRepository, CurrentUserProvider currentUserProvider) {
        this.placeRepository = placeRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public TimelineReconciliationOutcome execute(TimelineVisitSignal signal) {
        Objects.requireNonNull(signal, "signal must not be null");
        UserId userId = currentUserProvider.currentUserId();

        Optional<TimelineMatchCandidate> exactMatch =
                placeRepository.findExactOrNearTimelineMatch(
                        userId,
                        signal.googlePlaceId(),
                        signal.coordinates(),
                        EXACT_MATCH_RADIUS_METERS);
        if (exactMatch.isPresent()) {
            TimelineMatchCandidate candidate = exactMatch.get();
            if (candidate.googlePlaceId() == null && signal.googlePlaceId() != null) {
                placeRepository.backfillGooglePlaceId(
                        userId, candidate.id(), signal.googlePlaceId());
            }
            PlaceVisitState current =
                    placeRepository
                            .findVisitState(userId, candidate.id())
                            .orElseGet(() -> PlaceVisitState.unvisited(candidate.id(), userId));
            PlaceVisitState updated =
                    current.apply(VisitStatus.VISITED, VisitSource.TIMELINE, signal.observedAt());
            if (!updated.equals(current)) {
                placeRepository.upsertVisitStates(List.of(updated));
            }
            if (updated.status() != VisitStatus.VISITED) {
                return TimelineReconciliationOutcome.SKIPPED_MANUAL_OVERRIDE;
            }
            return TimelineReconciliationOutcome.MATCHED_VISITED;
        }

        Optional<PlaceId> triagePlace =
                placeRepository.findUnvisitedTriageCandidate(
                        userId,
                        signal.coordinates(),
                        EXACT_MATCH_RADIUS_METERS,
                        TRIAGE_MAX_RADIUS_METERS);
        if (triagePlace.isPresent()) {
            PlaceId placeId = triagePlace.get();
            PlaceVisitState current =
                    placeRepository
                            .findVisitState(userId, placeId)
                            .orElseGet(() -> PlaceVisitState.unvisited(placeId, userId));
            PlaceVisitState updated =
                    current.apply(
                            VisitStatus.NEEDS_TRIAGE, VisitSource.TIMELINE, signal.observedAt());
            if (!updated.equals(current)) {
                placeRepository.upsertVisitStates(List.of(updated));
            }
            if (updated.status() != VisitStatus.NEEDS_TRIAGE) {
                return updated.source() != null && updated.source().isManual()
                        ? TimelineReconciliationOutcome.SKIPPED_MANUAL_OVERRIDE
                        : TimelineReconciliationOutcome.UNMATCHED;
            }
            return TimelineReconciliationOutcome.MARKED_NEEDS_TRIAGE;
        }

        return TimelineReconciliationOutcome.UNMATCHED;
    }
}
