package com.meanderatlas.atlas.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.meanderatlas.atlas.AttachCsvMembershipCommand;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttachCsvMembershipByCidTest {

    private static final UserId USER_ID =
            new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    void returnsEmptyOnMissingCidAndAttachesCsvOrReviewWhenMatched() {
        FakeAtlasPlaceRepository repo = new FakeAtlasPlaceRepository();
        AttachCsvMembershipByCid useCase =
                new AttachCsvMembershipByCid(repo, () -> USER_ID, new ImportedPlaceMerger(repo));

        Optional<PlaceId> missing =
                useCase.execute(
                        new AttachCsvMembershipCommand(
                                999L,
                                "Missing Place",
                                null,
                                PlaceCategory.MUSEUM,
                                "museum",
                                CategorySource.HEURISTIC,
                                "personal",
                                "Want to go",
                                ListKind.WANT_TO_GO,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null));
        assertThat(missing).isEmpty();

        PlaceId placeId =
                repo.insertImportedPlace(
                        USER_ID,
                        "Old Castle",
                        new GeoPoint(50.0, 19.9),
                        CoordSource.TAKEOUT,
                        null,
                        777L,
                        PlaceCategory.UNCATEGORIZED,
                        null,
                        CategorySource.HEURISTIC,
                        DerivedRole.BACKLOG,
                        "PL",
                        "Krakow");

        Optional<PlaceId> attachedCsv =
                useCase.execute(
                        new AttachCsvMembershipCommand(
                                777L,
                                "Old Castle",
                                new GeoPoint(51.0, 20.0),
                                PlaceCategory.LANDMARK,
                                "castle",
                                CategorySource.HEURISTIC,
                                "secondary",
                                "Starred",
                                ListKind.STARRED,
                                "Great views",
                                "history",
                                "https://maps.google.com/?cid=777",
                                null,
                                null,
                                null,
                                null));

        assertThat(attachedCsv).contains(placeId);
        assertThat(repo.place(placeId).coordSource).isEqualTo(CoordSource.TAKEOUT);
        assertThat(repo.place(placeId).coordinates).isEqualTo(new GeoPoint(50.0, 19.9));
        assertThat(repo.place(placeId).category).isEqualTo(PlaceCategory.LANDMARK);
        assertThat(repo.place(placeId).derivedRole).isEqualTo(DerivedRole.MUST_SEE);
        assertThat(repo.membershipsOf(placeId)).hasSize(1);

        Instant reviewedAt = Instant.parse("2026-07-01T10:00:00Z");
        Optional<PlaceId> attachedReview =
                useCase.execute(
                        new AttachCsvMembershipCommand(
                                777L,
                                "Old Castle",
                                null,
                                PlaceCategory.LANDMARK,
                                "castle",
                                CategorySource.HEURISTIC,
                                "personal",
                                "Google Reviews",
                                ListKind.REVIEW,
                                "Visited last summer",
                                null,
                                "https://maps.google.com/?cid=777",
                                reviewedAt,
                                VisitStatus.VISITED,
                                VisitSource.REVIEW,
                                reviewedAt));

        assertThat(attachedReview).contains(placeId);
        assertThat(repo.visitState(placeId).status()).isEqualTo(VisitStatus.VISITED);
        assertThat(repo.visitState(placeId).source()).isEqualTo(VisitSource.REVIEW);
        assertThat(repo.visitState(placeId).visitCount()).isEqualTo(1);
    }

    @Test
    void upgradesApproximateS2CoordinatesToExactTakeoutWhenExactCoordinatesProvided() {
        FakeAtlasPlaceRepository repo = new FakeAtlasPlaceRepository();
        AttachCsvMembershipByCid useCase =
                new AttachCsvMembershipByCid(repo, () -> USER_ID, new ImportedPlaceMerger(repo));

        PlaceId s2PlaceId =
                repo.insertImportedPlace(
                        USER_ID,
                        "Approx Spot",
                        new GeoPoint(47.90, 106.90),
                        CoordSource.TAKEOUT_S2_APPROX,
                        null,
                        2002L,
                        PlaceCategory.UNCATEGORIZED,
                        null,
                        CategorySource.HEURISTIC,
                        DerivedRole.BACKLOG,
                        null,
                        null);

        GeoPoint exactCoords = new GeoPoint(47.9200833, 106.9055);
        Optional<PlaceId> upgraded =
                useCase.execute(
                        new AttachCsvMembershipCommand(
                                2002L,
                                "47°55'12.3\"N 106°54'19.8\"E",
                                exactCoords,
                                PlaceCategory.UNCATEGORIZED,
                                null,
                                CategorySource.HEURISTIC,
                                "personal",
                                "Want to go",
                                ListKind.WANT_TO_GO,
                                null,
                                null,
                                "https://www.google.com/maps/place/Spot/data=!4m2!3m1!1s0x1:0x7d2",
                                null,
                                null,
                                null,
                                null));

        assertThat(upgraded).contains(s2PlaceId);
        assertThat(repo.place(s2PlaceId).coordSource).isEqualTo(CoordSource.TAKEOUT);
        assertThat(repo.place(s2PlaceId).coordinates).isEqualTo(exactCoords);
        assertThat(repo.membershipsOf(s2PlaceId)).hasSize(1);
    }
}
