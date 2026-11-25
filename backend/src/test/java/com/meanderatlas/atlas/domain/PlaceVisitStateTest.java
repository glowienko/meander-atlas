package com.meanderatlas.atlas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlaceVisitStateTest {

    private static final UserId USER_ID =
            new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final PlaceId PLACE_ID =
            new PlaceId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
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
                initial.apply(VisitStatus.VISITED, VisitSource.TIMELINE, LATER_TIME);
        PlaceVisitState second =
                first.apply(VisitStatus.VISITED, VisitSource.TIMELINE, EARLIER_TIME);

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
                        .apply(VisitStatus.VISITED, VisitSource.TIMELINE, EARLIER_TIME);

        PlaceVisitState reviewed =
                timeline.apply(VisitStatus.VISITED, VisitSource.REVIEW, LATER_TIME);
        PlaceVisitState afterReimport =
                reviewed.apply(VisitStatus.VISITED, VisitSource.TIMELINE, LATEST_TIME);

        assertThat(reviewed.source()).isEqualTo(VisitSource.REVIEW);
        assertThat(reviewed.visitCount()).isEqualTo(TWO_VISITS);
        assertThat(afterReimport).isEqualTo(reviewed);
    }

    @Test
    void manualVisitedAndManualUnvisitedWinOverAutomatedReimports() {
        PlaceVisitState reviewed =
                PlaceVisitState.unvisited(PLACE_ID, USER_ID)
                        .apply(VisitStatus.VISITED, VisitSource.REVIEW, EARLIER_TIME);

        PlaceVisitState manualUnvisited =
                reviewed.apply(VisitStatus.UNVISITED, VisitSource.MANUAL_SINGLE, null);
        PlaceVisitState afterReviewReimport =
                manualUnvisited.apply(VisitStatus.VISITED, VisitSource.REVIEW, LATER_TIME);
        PlaceVisitState manualBulkVisited =
                afterReviewReimport.apply(
                        VisitStatus.VISITED, VisitSource.MANUAL_BULK, LATEST_TIME);
        PlaceVisitState afterTimelineReimport =
                manualBulkVisited.apply(
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
                        .apply(VisitStatus.NEEDS_TRIAGE, VisitSource.TIMELINE, EARLIER_TIME);
        PlaceVisitState visited =
                triage.apply(VisitStatus.VISITED, VisitSource.TIMELINE, LATER_TIME);
        PlaceVisitState stillVisited =
                visited.apply(VisitStatus.NEEDS_TRIAGE, VisitSource.TIMELINE, LATEST_TIME);

        assertThat(triage.status()).isEqualTo(VisitStatus.NEEDS_TRIAGE);
        assertThat(triage.visitCount()).isEqualTo(ZERO_VISITS);
        assertThat(stillVisited).isEqualTo(visited);
    }

    @Test
    void deduplicatesRepeatedTimelineVisitsWithinObservedRangeAndTracksNewBounds() {
        PlaceVisitState initial = PlaceVisitState.unvisited(PLACE_ID, USER_ID);

        PlaceVisitState first =
                initial.apply(VisitStatus.VISITED, VisitSource.TIMELINE, EARLIER_TIME);
        PlaceVisitState repeatedSame =
                first.apply(VisitStatus.VISITED, VisitSource.TIMELINE, EARLIER_TIME);
        PlaceVisitState second =
                repeatedSame.apply(VisitStatus.VISITED, VisitSource.TIMELINE, LATEST_TIME);
        PlaceVisitState repeatedInside =
                second.apply(VisitStatus.VISITED, VisitSource.TIMELINE, LATER_TIME);
        PlaceVisitState repeatedNull =
                repeatedInside.apply(VisitStatus.VISITED, VisitSource.TIMELINE, null);

        assertThat(repeatedSame).isEqualTo(first);
        assertThat(second.visitCount()).isEqualTo(TWO_VISITS);
        assertThat(second.firstVisitedAt()).isEqualTo(EARLIER_TIME);
        assertThat(second.lastVisitedAt()).isEqualTo(LATEST_TIME);
        assertThat(repeatedInside).isEqualTo(second);
        assertThat(repeatedNull).isEqualTo(second);
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
