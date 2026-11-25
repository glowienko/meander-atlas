package com.meanderatlas.atlas;

import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record VisitStateSnapshotView(
        @NotNull UUID placeId,
        @NotNull VisitStatus status,
        @Nullable VisitSource source,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Min(0) int visitCount,
        @Nullable Instant firstVisitedAt,
        @Nullable Instant lastVisitedAt) {}
