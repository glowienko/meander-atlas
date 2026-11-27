package com.meanderatlas.ingestion.domain.source;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record ImportStatusSummary(
        int pendingResolutionCount,
        int pendingCoordinateLookupCount,
        int pendingEnrichmentCount,
        int enrichedPlaceCount,
        int approximatePlaceCount,
        int completedBatches,
        List<String> accounts,
        @Nullable Instant lastImportedAt) {}
