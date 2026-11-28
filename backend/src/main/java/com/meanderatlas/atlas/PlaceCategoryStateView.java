package com.meanderatlas.atlas;

import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.PlaceCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record PlaceCategoryStateView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID placeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PlaceCategory category,
        @Nullable String subcategory,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CategorySource categorySource) {}
