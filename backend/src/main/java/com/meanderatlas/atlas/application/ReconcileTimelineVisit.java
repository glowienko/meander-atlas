package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.TimelineReconciliationOutcome;
import com.meanderatlas.atlas.TimelineVisitSignal;
import com.meanderatlas.atlas.domain.PlaceVisitState;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.atlas.domain.places.TimelineMatchCandidate;
import com.meanderatlas.atlas.domain.places.TimelineMatchCandidate.MatchConfidence;
import com.meanderatlas.shared.CurrentUserProvider;
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
        return placeRepository
                .findTimelineMatchCandidate(
                        userId,
                        signal.googlePlaceId(),
                        signal.coordinates(),
                        EXACT_MATCH_RADIUS_METERS,
                        TRIAGE_MAX_RADIUS_METERS)
                .map(candidate -> reconcileCandidate(userId, candidate, signal))
                .orElse(TimelineReconciliationOutcome.UNMATCHED);
    }

    private TimelineReconciliationOutcome reconcileCandidate(
            UserId userId, TimelineMatchCandidate candidate, TimelineVisitSignal signal) {
        Optional.ofNullable(signal.googlePlaceId())
                .filter(_ -> candidate.acceptsGooglePlaceIdBackfill())
                .ifPresent(id -> placeRepository.backfillGooglePlaceId(userId, candidate.id(), id));

        ReconciliationPolicy policy = ReconciliationPolicy.forConfidence(candidate.confidence());
        PlaceVisitState current = candidate.visitState();
        PlaceVisitState updated =
                current.apply(policy.targetStatus(), VisitSource.TIMELINE, signal.observedAt());
        if (!updated.equals(current)) {
            placeRepository.upsertVisitStates(List.of(updated));
        }
        return policy.classifyOutcome(updated);
    }

    private enum ReconciliationPolicy {
        EXACT(VisitStatus.VISITED, TimelineReconciliationOutcome.MATCHED_VISITED),
        TRIAGE(VisitStatus.NEEDS_TRIAGE, TimelineReconciliationOutcome.MARKED_NEEDS_TRIAGE);

        private final VisitStatus targetStatus;
        private final TimelineReconciliationOutcome appliedOutcome;

        ReconciliationPolicy(
                VisitStatus targetStatus, TimelineReconciliationOutcome appliedOutcome) {
            this.targetStatus = targetStatus;
            this.appliedOutcome = appliedOutcome;
        }

        static ReconciliationPolicy forConfidence(MatchConfidence confidence) {
            return switch (confidence) {
                case EXACT -> EXACT;
                case TRIAGE -> TRIAGE;
            };
        }

        VisitStatus targetStatus() {
            return targetStatus;
        }

        TimelineReconciliationOutcome classifyOutcome(PlaceVisitState updated) {
            if (updated.status() == targetStatus) {
                return appliedOutcome;
            }
            return updated.isManual()
                    ? TimelineReconciliationOutcome.SKIPPED_MANUAL_OVERRIDE
                    : TimelineReconciliationOutcome.UNMATCHED;
        }
    }
}
