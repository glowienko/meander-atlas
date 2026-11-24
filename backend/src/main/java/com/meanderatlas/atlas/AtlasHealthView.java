package com.meanderatlas.atlas;

import jakarta.validation.constraints.NotNull;

public record AtlasHealthView(
        @NotNull String status, @NotNull String database, @NotNull String postgisVersion) {

    public static final String STATUS_UP = "UP";
    public static final String DATABASE_POSTGRESQL = "postgresql";

    public static AtlasHealthView up(String postgisVersion) {
        return new AtlasHealthView(STATUS_UP, DATABASE_POSTGRESQL, postgisVersion);
    }
}
