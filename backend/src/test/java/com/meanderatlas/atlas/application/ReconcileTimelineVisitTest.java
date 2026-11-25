package com.meanderatlas.atlas.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.meanderatlas.atlas.TimelineReconciliationOutcome;
import com.meanderatlas.atlas.TimelineVisitSignal;
import com.meanderatlas.atlas.domain.PlaceVisitState;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconcileTimelineVisitTest {

    private static final UserId USER_ID =
            new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    void handlesExactMatchIdempotencyTriageUnmatchedAndManualOverrideOutcome() {
        FakeAtlasPlaceRepository repo = new FakeAtlasPlaceRepository();
        ReconcileTimelineVisit useCase = new ReconcileTimelineVisit(repo, () -> USER_ID);

        PlaceId exactPlace =
                repo.insertImportedPlace(
                        USER_ID,
                        "Wawel",
                        new GeoPoint(50.0540, 19.9354),
                        CoordSource.TAKEOUT,
                        null,
                        111L,
                        PlaceCategory.LANDMARK,
                        "castle",
                        CategorySource.HEURISTIC,
                        DerivedRole.MUST_SEE,
                        "PL",
                        "Krakow");
        PlaceId triagePlace =
                repo.insertImportedPlace(
                        USER_ID,
                        "Nearby Spot",
                        new GeoPoint(50.0600, 19.9500),
                        CoordSource.TAKEOUT,
                        null,
                        222L,
                        PlaceCategory.CAFE_BAR,
                        "cafe",
                        CategorySource.HEURISTIC,
                        DerivedRole.BACKLOG,
                        "PL",
                        "Krakow");

        Instant t1 = Instant.parse("2026-08-01T10:00:00Z");
        Instant t2 = Instant.parse("2026-08-05T10:00:00Z");

        assertThat(
                        useCase.execute(
                                new TimelineVisitSignal(
                                        "ChIJ-wawel", new GeoPoint(50.0540, 19.9354), t1)))
                .isEqualTo(TimelineReconciliationOutcome.MATCHED_VISITED);
        assertThat(repo.place(exactPlace).googlePlaceId).isEqualTo("ChIJ-wawel");
        assertThat(repo.visitState(exactPlace).visitCount()).isEqualTo(1);

        assertThat(
                        useCase.execute(
                                new TimelineVisitSignal(
                                        "ChIJ-wawel", new GeoPoint(50.0540, 19.9354), t1)))
                .isEqualTo(TimelineReconciliationOutcome.MATCHED_VISITED);
        assertThat(repo.visitState(exactPlace).visitCount()).isEqualTo(1);

        assertThat(
                        useCase.execute(
                                new TimelineVisitSignal(
                                        "ChIJ-wawel", new GeoPoint(50.0540, 19.9354), t2)))
                .isEqualTo(TimelineReconciliationOutcome.MATCHED_VISITED);
        assertThat(repo.visitState(exactPlace).visitCount()).isEqualTo(2);

        repo.upsertVisitStates(
                List.of(
                        new PlaceVisitState(
                                exactPlace,
                                USER_ID,
                                VisitStatus.UNVISITED,
                                VisitSource.MANUAL_SINGLE,
                                0,
                                null,
                                null)));
        assertThat(
                        useCase.execute(
                                new TimelineVisitSignal(
                                        "ChIJ-wawel", new GeoPoint(50.0540, 19.9354), t2)))
                .isEqualTo(TimelineReconciliationOutcome.SKIPPED_MANUAL_OVERRIDE);

        assertThat(
                        useCase.execute(
                                new TimelineVisitSignal(null, new GeoPoint(50.06072, 19.9500), t2)))
                .isEqualTo(TimelineReconciliationOutcome.MARKED_NEEDS_TRIAGE);
        assertThat(repo.visitState(triagePlace).status()).isEqualTo(VisitStatus.NEEDS_TRIAGE);

        repo.upsertVisitStates(
                List.of(
                        new PlaceVisitState(
                                triagePlace,
                                USER_ID,
                                VisitStatus.UNVISITED,
                                VisitSource.MANUAL_BULK,
                                0,
                                null,
                                null)));
        assertThat(
                        useCase.execute(
                                new TimelineVisitSignal(null, new GeoPoint(50.06072, 19.9500), t2)))
                .isEqualTo(TimelineReconciliationOutcome.SKIPPED_MANUAL_OVERRIDE);

        assertThat(useCase.execute(new TimelineVisitSignal(null, new GeoPoint(52.0, 21.0), t2)))
                .isEqualTo(TimelineReconciliationOutcome.UNMATCHED);
    }

    @Test
    void triageCandidateIsNotMaskedByCloserVisitedPlaceWithinTriageRadius() {
        FakeAtlasPlaceRepository repo = new FakeAtlasPlaceRepository();
        ReconcileTimelineVisit useCase = new ReconcileTimelineVisit(repo, () -> USER_ID);

        PlaceId visitedCloser =
                repo.insertImportedPlace(
                        USER_ID,
                        "Closer Visited Bistro",
                        new GeoPoint(50.06000, 19.95000),
                        CoordSource.TAKEOUT,
                        null,
                        333L,
                        PlaceCategory.RESTAURANT,
                        "bistro",
                        CategorySource.HEURISTIC,
                        DerivedRole.BACKLOG,
                        "PL",
                        "Krakow");
        Instant reviewAt = Instant.parse("2026-07-15T12:00:00Z");
        repo.upsertVisitStates(
                List.of(
                        new PlaceVisitState(
                                visitedCloser,
                                USER_ID,
                                VisitStatus.VISITED,
                                VisitSource.REVIEW,
                                1,
                                reviewAt,
                                reviewAt)));

        PlaceId unvisitedFurther =
                repo.insertImportedPlace(
                        USER_ID,
                        "Further Unvisited Cafe",
                        new GeoPoint(50.06126, 19.95000),
                        CoordSource.TAKEOUT,
                        null,
                        444L,
                        PlaceCategory.CAFE_BAR,
                        "cafe",
                        CategorySource.HEURISTIC,
                        DerivedRole.BACKLOG,
                        "PL",
                        "Krakow");

        Instant visitAt = Instant.parse("2026-08-20T14:00:00Z");
        assertThat(
                        useCase.execute(
                                new TimelineVisitSignal(
                                        null, new GeoPoint(50.06054, 19.95000), visitAt)))
                .isEqualTo(TimelineReconciliationOutcome.MARKED_NEEDS_TRIAGE);
        assertThat(repo.visitState(unvisitedFurther).status()).isEqualTo(VisitStatus.NEEDS_TRIAGE);
        assertThat(repo.visitState(visitedCloser).status()).isEqualTo(VisitStatus.VISITED);
        assertThat(repo.visitState(visitedCloser).source()).isEqualTo(VisitSource.REVIEW);

        assertThat(
                        useCase.execute(
                                new TimelineVisitSignal(
                                        null, new GeoPoint(50.06000, 19.95000), visitAt)))
                .isEqualTo(TimelineReconciliationOutcome.MATCHED_VISITED);
        assertThat(repo.visitState(visitedCloser).source()).isEqualTo(VisitSource.REVIEW);
    }
}
