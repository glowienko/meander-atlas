package com.meanderatlas.atlas;

import jakarta.validation.constraints.NotNull;

public record AtlasHealthView(
        @NotNull String status, @NotNull String database, @NotNull String postgisVersion) {}
