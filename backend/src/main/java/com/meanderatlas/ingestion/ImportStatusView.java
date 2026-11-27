package com.meanderatlas.ingestion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record ImportStatusView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int pendingResolutionCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int pendingCoordinateLookupCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int pendingEnrichmentCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int enrichedPlaceCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int approximatePlaceCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean enrichmentRunning,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int completedBatches,
        @NotNull List<String> accounts,
        @Nullable Instant lastImportedAt) {}
