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
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttachCsvMembershipByCidTest {

    private static final UserId USER_ID =
            new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    void returnsFalseOnMissingCidAndAttachesCsvOrReviewWhenMatched() {
        FakeAtlasPlaceRepository repo = new FakeAtlasPlaceRepository();
        AttachCsvMembershipByCid useCase = new AttachCsvMembershipByCid(repo, () -> USER_ID);

        boolean missing =
                useCase.execute(
                        new AttachCsvMembershipCommand(
                                999L,
                                "Missing Place",
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
        assertThat(missing).isFalse();

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

        boolean attachedCsv =
                useCase.execute(
                        new AttachCsvMembershipCommand(
                                777L,
                                "Old Castle",
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

        assertThat(attachedCsv).isTrue();
        assertThat(repo.place(placeId).category).isEqualTo(PlaceCategory.LANDMARK);
        assertThat(repo.place(placeId).derivedRole).isEqualTo(DerivedRole.MUST_SEE);
        assertThat(repo.membershipsOf(placeId)).hasSize(1);

        Instant reviewedAt = Instant.parse("2026-07-01T10:00:00Z");
        boolean attachedReview =
                useCase.execute(
                        new AttachCsvMembershipCommand(
                                777L,
                                "Old Castle",
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

        assertThat(attachedReview).isTrue();
        assertThat(repo.visitState(placeId).status()).isEqualTo(VisitStatus.VISITED);
        assertThat(repo.visitState(placeId).source()).isEqualTo(VisitSource.REVIEW);
        assertThat(repo.visitState(placeId).visitCount()).isEqualTo(1);
    }
}
