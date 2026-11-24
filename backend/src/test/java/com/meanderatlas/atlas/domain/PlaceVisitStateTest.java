package com.meanderatlas.atlas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import com.meanderatlas.shared.VisitSource;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlaceVisitStateTest {

    private static final UserId USER_ID =
            new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final UserId OTHER_USER_ID =
            new UserId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    private static final PlaceId PLACE_ID =
            new PlaceId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final PlaceId OTHER_PLACE_ID =
            new PlaceId(UUID.fromString("10000000-0000-0000-0000-000000000002"));
    private static final Instant EARLIER_TIME = Instant.parse("2025-05-01T10:00:00Z");
    private static final Instant LATER_TIME = Instant.parse("2025-08-15T14:30:00Z");
    private static final Instant LATEST_TIME = Instant.parse("2026-01-10T09:00:00Z");
    private static final int ZERO_VISITS = 0;
    private static final int ONE_VISIT = 1;
    private static final int TWO_VISITS = 2;
    private static final int NEGATIVE_VISITS = -1;

    @Test
    void timelineVisitsPromoteUnvisitedAndTrackVisitBounds() {
        PlaceVisitState initial = PlaceVisitState.unvisited(PLACE_ID, USER_ID);

        PlaceVisitState first =
                initial.recordSignal(VisitStatus.VISITED, VisitSource.TIMELINE, LATER_TIME);
        PlaceVisitState second =
                first.recordSignal(VisitStatus.VISITED, VisitSource.TIMELINE, EARLIER_TIME);

        assertThat(second.status()).isEqualTo(VisitStatus.VISITED);
        assertThat(second.source()).isEqualTo(VisitSource.TIMELINE);
        assertThat(second.visitCount()).isEqualTo(TWO_VISITS);
        assertThat(second.firstVisitedAt()).isEqualTo(EARLIER_TIME);
        assertThat(second.lastVisitedAt()).isEqualTo(LATER_TIME);
    }

    @Test
    void reviewOverridesTimelineAndTimelineNeverClobbersReview() {
        PlaceVisitState timeline =
                PlaceVisitState.unvisited(PLACE_ID, USER_ID)
                        .recordSignal(VisitStatus.VISITED, VisitSource.TIMELINE, EARLIER_TIME);

        PlaceVisitState reviewed =
                timeline.recordSignal(VisitStatus.VISITED, VisitSource.REVIEW, LATER_TIME);
        PlaceVisitState afterReimport =
                reviewed.recordSignal(VisitStatus.VISITED, VisitSource.TIMELINE, LATEST_TIME);

        assertThat(reviewed.source()).isEqualTo(VisitSource.REVIEW);
        assertThat(reviewed.visitCount()).isEqualTo(TWO_VISITS);
        assertThat(afterReimport).isEqualTo(reviewed);
    }

    @Test
    void manualVisitedAndManualUnvisitedWinOverAutomatedReimports() {
        PlaceVisitState reviewed =
                PlaceVisitState.unvisited(PLACE_ID, USER_ID)
                        .recordSignal(VisitStatus.VISITED, VisitSource.REVIEW, EARLIER_TIME);

        PlaceVisitState manualUnvisited =
                reviewed.recordSignal(VisitStatus.UNVISITED, VisitSource.MANUAL_SINGLE, null);
        PlaceVisitState afterReviewReimport =
                manualUnvisited.recordSignal(VisitStatus.VISITED, VisitSource.REVIEW, LATER_TIME);
        PlaceVisitState manualBulkVisited =
                afterReviewReimport.recordSignal(
                        VisitStatus.VISITED, VisitSource.MANUAL_BULK, LATEST_TIME);
        PlaceVisitState afterTimelineReimport =
                manualBulkVisited.recordSignal(
                        VisitStatus.NEEDS_TRIAGE, VisitSource.TIMELINE, LATEST_TIME);

        assertThat(manualUnvisited.status()).isEqualTo(VisitStatus.UNVISITED);
        assertThat(manualUnvisited.source()).isEqualTo(VisitSource.MANUAL_SINGLE);
        assertThat(manualUnvisited.visitCount()).isEqualTo(ZERO_VISITS);
        assertThat(afterReviewReimport).isEqualTo(manualUnvisited);
        assertThat(manualBulkVisited.status()).isEqualTo(VisitStatus.VISITED);
        assertThat(manualBulkVisited.source()).isEqualTo(VisitSource.MANUAL_BULK);
        assertThat(manualBulkVisited.visitCount()).isEqualTo(ONE_VISIT);
        assertThat(afterTimelineReimport).isEqualTo(manualBulkVisited);
    }

    @Test
    void needsTriageMarksUnvisitedButNeverDowngradesVisited() {
        PlaceVisitState triage =
                PlaceVisitState.unvisited(PLACE_ID, USER_ID)
                        .recordSignal(VisitStatus.NEEDS_TRIAGE, VisitSource.TIMELINE, EARLIER_TIME);
        PlaceVisitState visited =
                triage.recordSignal(VisitStatus.VISITED, VisitSource.TIMELINE, LATER_TIME);
        PlaceVisitState stillVisited =
                visited.recordSignal(VisitStatus.NEEDS_TRIAGE, VisitSource.TIMELINE, LATEST_TIME);

        assertThat(triage.status()).isEqualTo(VisitStatus.NEEDS_TRIAGE);
        assertThat(triage.visitCount()).isEqualTo(ZERO_VISITS);
        assertThat(stillVisited).isEqualTo(visited);
    }

    @Test
    void restoresExactPreviousSnapshotAndRejectsMismatchedIdentity() {
        PlaceVisitState previous =
                PlaceVisitState.unvisited(PLACE_ID, USER_ID)
                        .recordSignal(VisitStatus.NEEDS_TRIAGE, VisitSource.TIMELINE, EARLIER_TIME);
        PlaceVisitState bulkMarked =
                previous.recordSignal(VisitStatus.VISITED, VisitSource.MANUAL_BULK, LATER_TIME);

        assertThat(bulkMarked.restoreFrom(previous)).isEqualTo(previous);

        PlaceVisitState otherPlace = PlaceVisitState.unvisited(OTHER_PLACE_ID, USER_ID);
        PlaceVisitState otherUser = PlaceVisitState.unvisited(PLACE_ID, OTHER_USER_ID);
        assertThatThrownBy(() -> bulkMarked.restoreFrom(otherPlace))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bulkMarked.restoreFrom(otherUser))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidStateInvariants() {
        assertThatThrownBy(() ->
                        new PlaceVisitState(
                                PLACE_ID,
                                USER_ID,
                                VisitStatus.UNVISITED,
                                null,
                                NEGATIVE_VISITS,
                                null,
                                null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new PlaceVisitState(
                                PLACE_ID,
                                USER_ID,
                                VisitStatus.VISITED,
                                null,
                                ONE_VISIT,
                                EARLIER_TIME,
                                EARLIER_TIME))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new PlaceVisitState(
                                PLACE_ID,
                                USER_ID,
                                VisitStatus.VISITED,
                                VisitSource.TIMELINE,
                                ZERO_VISITS,
                                EARLIER_TIME,
                                EARLIER_TIME))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new PlaceVisitState(
                                PLACE_ID,
                                USER_ID,
                                VisitStatus.UNVISITED,
                                VisitSource.MANUAL_SINGLE,
                                ONE_VISIT,
                                null,
                                null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
