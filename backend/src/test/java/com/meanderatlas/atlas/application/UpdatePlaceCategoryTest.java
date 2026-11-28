package com.meanderatlas.atlas.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meanderatlas.atlas.PlaceCategoryStateView;
import com.meanderatlas.atlas.UpdatePlaceCategoryRequest;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdatePlaceCategoryTest {

    private static final UserId USER_ID =
            new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    void updatesCategoryToManualAndNormalizesSubcategory() {
        FakeAtlasPlaceRepository repo = new FakeAtlasPlaceRepository();
        PlaceId placeId =
                repo.insertImportedPlace(
                        USER_ID,
                        "Supermercado El Cotillo",
                        new GeoPoint(28.683, -14.010),
                        CoordSource.TAKEOUT,
                        null,
                        null,
                        PlaceCategory.UNCATEGORIZED,
                        null,
                        CategorySource.HEURISTIC,
                        DerivedRole.BACKLOG,
                        "ES",
                        "El Cotillo");
        UpdatePlaceCategory useCase = new UpdatePlaceCategory(repo, () -> USER_ID);

        PlaceCategoryStateView updated =
                useCase.execute(
                        placeId.value(),
                        new UpdatePlaceCategoryRequest(PlaceCategory.GROCERIES, "  supermarket  "));

        assertThat(updated.placeId()).isEqualTo(placeId.value());
        assertThat(updated.category()).isEqualTo(PlaceCategory.GROCERIES);
        assertThat(updated.subcategory()).isEqualTo("supermarket");
        assertThat(updated.categorySource()).isEqualTo(CategorySource.MANUAL);
        assertThat(repo.place(placeId).category).isEqualTo(PlaceCategory.GROCERIES);
        assertThat(repo.place(placeId).categorySource).isEqualTo(CategorySource.MANUAL);

        PlaceCategoryStateView clearedSubcategory =
                useCase.execute(
                        placeId.value(),
                        new UpdatePlaceCategoryRequest(PlaceCategory.PHARMACY, "   "));
        assertThat(clearedSubcategory.category()).isEqualTo(PlaceCategory.PHARMACY);
        assertThat(clearedSubcategory.subcategory()).isNull();
    }

    @Test
    void rejectsUnknownOrForeignPlaceId() {
        FakeAtlasPlaceRepository repo = new FakeAtlasPlaceRepository();
        UpdatePlaceCategory useCase = new UpdatePlaceCategory(repo, () -> USER_ID);

        assertThatThrownBy(() ->
                        useCase.execute(
                                UUID.randomUUID(),
                                new UpdatePlaceCategoryRequest(PlaceCategory.GROCERIES, null)))
                .isInstanceOf(NoSuchElementException.class);
    }
}
