package com.meanderatlas.atlas;

import com.meanderatlas.shared.PlaceCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CategoryStatView(
        @NotNull PlaceCategory category,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int visitedCount) {}
