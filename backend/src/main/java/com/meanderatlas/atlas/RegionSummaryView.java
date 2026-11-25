package com.meanderatlas.atlas;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RegionSummaryView(
        @NotNull UUID id,
        @NotNull RegionScope level,
        @NotNull String code,
        @NotNull String countryCode,
        @NotNull String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPlaces,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int visitedPlaces,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int unvisitedPlaces,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int needsTriagePlaces,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double completionRatio,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double minLng,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double minLat,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double maxLng,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double maxLat) {}
