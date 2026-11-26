package com.meanderatlas.atlas;

import io.swagger.v3.oas.annotations.media.Schema;

public record BoundarySeedResultView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int countriesUpserted,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int admin1Upserted,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int placesTagged,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean skipped) {}
