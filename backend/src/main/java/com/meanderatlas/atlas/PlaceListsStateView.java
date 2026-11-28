package com.meanderatlas.atlas;

import com.meanderatlas.shared.DerivedRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record PlaceListsStateView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID placeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DerivedRole derivedRole,
        @NotNull List<PlaceMembershipView> memberships) {}
