package com.meanderatlas.atlas;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AtlasPlacesResponse(
        @NotNull List<AtlasPlaceView> places,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalCount) {}
