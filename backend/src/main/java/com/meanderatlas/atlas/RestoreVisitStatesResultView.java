package com.meanderatlas.atlas;

import io.swagger.v3.oas.annotations.media.Schema;

public record RestoreVisitStatesResultView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int restoredCount) {}
