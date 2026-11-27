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
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int remainingPendingCount) {

    public static EnrichmentBatchResultView disabled(boolean dryRun, int remaining) {
        return new EnrichmentBatchResultView(
                false, dryRun, false, false, 0, 0, 0, 0, 0, 0, 0, remaining);
    }

    public static EnrichmentBatchResultView idle(boolean dryRun, int remaining) {
        return new EnrichmentBatchResultView(
                true, dryRun, false, false, 0, 0, 0, 0, 0, 0, 0, remaining);
    }

    public static EnrichmentBatchResultView busy(boolean dryRun, int remaining) {
        return new EnrichmentBatchResultView(
                true, dryRun, false, true, 0, 0, 0, 0, 0, 0, 0, remaining);
    }

    public static EnrichmentBatchResultView backgroundStarted(boolean dryRun, int remaining) {
        return new EnrichmentBatchResultView(
                true, dryRun, true, false, 0, 0, 0, 0, 0, 0, 0, remaining);
    }
}
