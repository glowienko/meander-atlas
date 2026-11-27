package com.meanderatlas.ingestion;

import io.swagger.v3.oas.annotations.media.Schema;

public record OfflineMaterializationSummaryView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int materializedCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int exactFromTitleCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int approxFromS2Count,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int mergedByCidCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int remainingLookupCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int enqueuedEnrichmentCount) {}
