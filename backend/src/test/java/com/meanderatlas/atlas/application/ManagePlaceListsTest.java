package com.meanderatlas.atlas.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meanderatlas.atlas.BulkAssignListRequest;
import com.meanderatlas.atlas.BulkAssignListResultView;
import com.meanderatlas.atlas.ListAssignMode;
import com.meanderatlas.atlas.PlaceListEntryRequest;
import com.meanderatlas.atlas.PlaceListsStateView;
import com.meanderatlas.atlas.UpdatePlaceListsRequest;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManagePlaceListsTest {

    private static final UserId USER_ID =
            new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    void bulkAssignCreatesAddsAndMovesCustomListsWhilePreservingReviews() {
        FakeAtlasPlaceRepository repo = new FakeAtlasPlaceRepository();
        PlaceId castleId = insertPlace(repo, "Wawel Castle", PlaceCategory.LANDMARK);
        PlaceId hotelId = insertPlace(repo, "Boutique Hotel", PlaceCategory.HOTEL);
        repo.upsertMembership(
                USER_ID,
                castleId,
                "personal",
                "Want to go",
                ListKind.WANT_TO_GO,
                null,
                null,
                null,
                null);
        repo.upsertMembership(
                USER_ID,
                castleId,
                "personal",
                "Reviews",
                ListKind.REVIEW,
                "Great!",
                null,
                null,
                null);
        repo.upsertMembership(
                USER_ID,
                hotelId,
                "personal",
                "Want to go",
                ListKind.WANT_TO_GO,
                null,
                null,
                null,
                null);

        BulkAssignPlacesToList useCase = new BulkAssignPlacesToList(repo, () -> USER_ID);

        BulkAssignListResultView added =
                useCase.execute(
                        new BulkAssignListRequest(
                                List.of(castleId.value(), hotelId.value(), castleId.value()),
                                "  Krakow Weekend  ",
                                ListKind.CUSTOM,
                                ListAssignMode.ADD,
                                null,
                                null));
        assertThat(added.assignedCount()).isEqualTo(2);
        assertThat(added.removedCount()).isZero();
        assertThat(added.accountLabel()).isEqualTo("personal");
        assertThat(added.targetListName()).isEqualTo("Krakow Weekend");
        assertThat(added.listKind()).isEqualTo(ListKind.CUSTOM);
        assertThat(repo.membershipsOf(castleId))
                .extracting("listName")
                .containsExactlyInAnyOrder("Want to go", "Reviews", "Krakow Weekend");

        BulkAssignListResultView moved =
                useCase.execute(
                        new BulkAssignListRequest(
                                List.of(castleId.value(), hotelId.value()),
                                "Starred Stays",
                                ListKind.STARRED,
                                ListAssignMode.MOVE,
                                null,
                                "personal"));
        assertThat(moved.assignedCount()).isEqualTo(2);
        assertThat(moved.removedCount()).isEqualTo(4);
        assertThat(repo.membershipsOf(castleId))
                .extracting("listName")
                .containsExactlyInAnyOrder("Reviews", "Starred Stays");
        assertThat(repo.place(castleId).derivedRole).isEqualTo(DerivedRole.MUST_SEE);
        assertThat(repo.place(hotelId).derivedRole).isEqualTo(DerivedRole.PREFERRED_HOTEL);
    }

    @Test
    void updatePlaceListsReplacesNonReviewMembershipsAndUpdatesDerivedRole() {
        FakeAtlasPlaceRepository repo = new FakeAtlasPlaceRepository();
        PlaceId placeId = insertPlace(repo, "Cafe Camelot", PlaceCategory.CAFE_BAR);
        repo.upsertMembership(
                USER_ID,
                placeId,
                "personal",
                "Want to go",
                ListKind.WANT_TO_GO,
                null,
                null,
                null,
                null);
        repo.upsertMembership(
                USER_ID, placeId, "personal", "Reviews", ListKind.REVIEW, "Nice", null, null, null);

        UpdatePlaceLists useCase = new UpdatePlaceLists(repo, () -> USER_ID);
        PlaceListsStateView updated =
                useCase.execute(
                        placeId.value(),
                        new UpdatePlaceListsRequest(
                                List.of(
                                        new PlaceListEntryRequest(
                                                "Favorites", ListKind.FAVORITE, null),
                                        new PlaceListEntryRequest(
                                                "Favorites", ListKind.FAVORITE, "personal"),
                                        new PlaceListEntryRequest(
                                                "Poland Cafes", ListKind.CUSTOM, "personal"))));

        assertThat(updated.placeId()).isEqualTo(placeId.value());
        assertThat(updated.derivedRole()).isEqualTo(DerivedRole.FAVORITE);
        assertThat(updated.memberships())
                .extracting("listName")
                .containsExactlyInAnyOrder("Reviews", "Favorites", "Poland Cafes");

        assertThatThrownBy(() ->
                        useCase.execute(UUID.randomUUID(), new UpdatePlaceListsRequest(List.of())))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() ->
                        useCase.execute(
                                placeId.value(),
                                new UpdatePlaceListsRequest(
                                        List.of(
                                                new PlaceListEntryRequest(
                                                        "   ", ListKind.CUSTOM, null)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PlaceId insertPlace(
            FakeAtlasPlaceRepository repo, String name, PlaceCategory category) {
        return repo.insertImportedPlace(
                USER_ID,
                name,
                new GeoPoint(50.06, 19.94),
                CoordSource.TAKEOUT,
                null,
                null,
                category,
                null,
                CategorySource.HEURISTIC,
                DerivedRole.BACKLOG,
                "PL",
                "Krakow");
    }
}
