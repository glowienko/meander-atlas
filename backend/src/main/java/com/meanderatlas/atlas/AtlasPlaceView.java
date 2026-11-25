package com.meanderatlas.atlas;

import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record AtlasPlaceView(
        @NotNull UUID id,
        @NotNull String canonicalName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double lat,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double lng,
        @NotNull PlaceCategory category,
        @Nullable String subcategory,
        @NotNull CategorySource categorySource,
        @NotNull DerivedRole derivedRole,
        @NotNull VisitStatus visitStatus,
        @Nullable VisitSource visitSource,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int visitCount,
        @Nullable Instant firstVisitedAt,
        @Nullable Instant lastVisitedAt,
        @Nullable String countryCode,
        @Nullable UUID admin1Id,
        @Nullable String admin1Code,
        @Nullable String admin1Name,
        @Nullable String cityName,
        @NotNull List<PlaceMembershipView> memberships) {}
