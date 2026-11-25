package com.meanderatlas.atlas;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AtlasStatsView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalMappedPlaces,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int visitedPlaces,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int unvisitedPlaces,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int needsTriagePlaces,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int visitedCountries,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalSavedCountries,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int visitedAdmin1Regions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalSavedAdmin1Regions,
        @NotNull List<CategoryStatView> categories,
        @NotNull List<SubcategoryStatView> topSubcategories,
        @NotNull List<AccountListFacetView> accountLists) {}
