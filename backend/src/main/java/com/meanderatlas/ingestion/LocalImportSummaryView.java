package com.meanderatlas.ingestion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record LocalImportSummaryView(
        @NotNull List<AccountImportSummaryView> accounts,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPlacesCreated,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPlacesMerged,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalCsvCidMatched,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalQueuedResolutionJobs,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalTimelineMatchedVisited,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalTimelineMarkedTriage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalTimelineUnmatched) {}
