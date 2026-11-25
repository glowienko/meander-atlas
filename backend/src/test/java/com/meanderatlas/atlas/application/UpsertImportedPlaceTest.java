package com.meanderatlas.atlas.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.meanderatlas.atlas.ImportedPlaceUpsertCommand;
import com.meanderatlas.atlas.UpsertedPlaceView;
import com.meanderatlas.atlas.domain.PlaceVisitState;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.UserId;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpsertImportedPlaceTest {

    private static final UserId USER_ID =
            new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    void createsPlaceMergesByCidUpgradesUncategorizedAndPreservesManualEdits() {
        FakeAtlasPlaceRepository repo = new FakeAtlasPlaceRepository();
        UpsertImportedPlace useCase =
                new UpsertImportedPlace(repo, () -> USER_ID, new ImportedPlaceMerger(repo));
        Instant savedAt = Instant.parse("2026-05-01T10:00:00Z");

        UpsertedPlaceView created =
                useCase.execute(
                        new ImportedPlaceUpsertCommand(
                                "Grand Hotel",
                                new GeoPoint(50.05, 19.94),
                                CoordSource.TAKEOUT,
                                null,
                                555L,
                                PlaceCategory.UNCATEGORIZED,
                                null,
                                CategorySource.HEURISTIC,
                                " pl ",
                                "Krakow",
                                "personal",
                                "Want to go",
                                ListKind.WANT_TO_GO,
                                null,
                                null,
                                "https://maps.google.com/?cid=555",
                                savedAt,
                                null,
                                null,
                                null));

        assertThat(created.created()).isTrue();
        assertThat(repo.place(created.placeId()).countryCode).isEqualTo("PL");
        assertThat(repo.place(created.placeId()).derivedRole).isEqualTo(DerivedRole.BACKLOG);
        assertThat(repo.visitState(created.placeId()).status()).isEqualTo(VisitStatus.UNVISITED);

        Instant reviewAt = Instant.parse("2026-06-01T12:00:00Z");
        UpsertedPlaceView mergedStarredHotel =
                useCase.execute(
                        new ImportedPlaceUpsertCommand(
                                "Grand Hotel",
                                new GeoPoint(50.05, 19.94),
                                CoordSource.TAKEOUT,
                                "ChIJ-grand-hotel",
                                555L,
                                PlaceCategory.HOTEL,
                                "hotel",
                                CategorySource.HEURISTIC,
                                "PL",
                                null,
                                "work",
                                "Starred",
                                ListKind.STARRED,
                                "Great lobby",
                                null,
                                "https://maps.google.com/?cid=555",
                                reviewAt,
                                VisitStatus.VISITED,
                                VisitSource.REVIEW,
                                reviewAt));

        assertThat(mergedStarredHotel.created()).isFalse();
        assertThat(mergedStarredHotel.placeId()).isEqualTo(created.placeId());
        var stored = repo.place(created.placeId());
        assertThat(stored.category).isEqualTo(PlaceCategory.HOTEL);
        assertThat(stored.subcategory).isEqualTo("hotel");
        assertThat(stored.derivedRole).isEqualTo(DerivedRole.PREFERRED_HOTEL);
        assertThat(stored.googlePlaceId).isEqualTo("ChIJ-grand-hotel");
        assertThat(repo.visitState(created.placeId()).status()).isEqualTo(VisitStatus.VISITED);
        assertThat(repo.visitState(created.placeId()).visitCount()).isEqualTo(1);

        useCase.execute(
                new ImportedPlaceUpsertCommand(
                        "Grand Hotel",
                        new GeoPoint(50.05, 19.94),
                        CoordSource.TAKEOUT,
                        "ChIJ-grand-hotel",
                        555L,
                        PlaceCategory.RESTAURANT,
                        "bistro",
                        CategorySource.HEURISTIC,
                        "PL",
                        null,
                        "work",
                        "Reviews",
                        ListKind.REVIEW,
                        "Second review import",
                        null,
                        null,
                        reviewAt,
                        VisitStatus.VISITED,
                        VisitSource.REVIEW,
                        reviewAt));

        assertThat(repo.place(created.placeId()).category).isEqualTo(PlaceCategory.HOTEL);
        assertThat(repo.visitState(created.placeId()).visitCount()).isEqualTo(1);

        repo.upsertVisitStates(
                List.of(
                        new PlaceVisitState(
                                created.placeId(),
                                USER_ID,
                                VisitStatus.UNVISITED,
                                VisitSource.MANUAL_SINGLE,
                                0,
                                null,
                                null)));
        useCase.execute(
                new ImportedPlaceUpsertCommand(
                        "Grand Hotel",
                        new GeoPoint(50.05, 19.94),
                        CoordSource.TAKEOUT,
                        "ChIJ-grand-hotel",
                        555L,
                        PlaceCategory.HOTEL,
                        "hotel",
                        CategorySource.HEURISTIC,
                        "PL",
                        null,
                        "work",
                        "Reviews",
                        ListKind.REVIEW,
                        "Blocked by manual",
                        null,
                        null,
                        reviewAt,
                        VisitStatus.VISITED,
                        VisitSource.REVIEW,
                        reviewAt));

        assertThat(repo.visitState(created.placeId()).status()).isEqualTo(VisitStatus.UNVISITED);
        assertThat(repo.visitState(created.placeId()).source())
                .isEqualTo(VisitSource.MANUAL_SINGLE);
    }
}
