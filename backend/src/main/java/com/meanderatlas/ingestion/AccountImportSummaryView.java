package com.meanderatlas.ingestion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AccountImportSummaryView(
        @NotNull String accountLabel,
        @NotNull String contentSha256,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean skipped,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int discoveredFiles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int importRecordsCreated,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int placesCreated,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int placesMerged,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int csvCidMatched,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int queuedResolutionJobs,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int timelineMatchedVisited,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int timelineMarkedTriage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int timelineUnmatched) {}
