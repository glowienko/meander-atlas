package com.meanderatlas.ingestion.domain.source;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record ImportStatusSummary(
        int pendingResolutionCount,
        int completedBatches,
        List<String> accounts,
        @Nullable Instant lastImportedAt) {}
