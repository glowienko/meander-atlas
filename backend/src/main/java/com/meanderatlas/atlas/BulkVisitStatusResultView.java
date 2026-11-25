package com.meanderatlas.atlas;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BulkVisitStatusResultView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int updatedCount,
        @NotNull List<VisitStateSnapshotView> previousStates) {}
