package com.meanderatlas.atlas;

import com.meanderatlas.shared.ListKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record BulkAssignListResultView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int assignedCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int removedCount,
        @NotNull String accountLabel,
        @NotNull String targetListName,
        @NotNull ListKind listKind) {}
