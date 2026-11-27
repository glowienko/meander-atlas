package com.meanderatlas.ingestion;

import io.swagger.v3.oas.annotations.media.Schema;

public record EnrichmentBatchResultView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean enabled,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean dryRun,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean startedInBackground,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean alreadyRunning,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int claimedCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int matchedCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int upgradedCoordsCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int upgradedCategoryCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int photosAddedCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int failedCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int skippedCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int remainingPendingCount) {}
