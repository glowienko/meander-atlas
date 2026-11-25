package com.meanderatlas.atlas;

import com.meanderatlas.shared.ListKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AccountListFacetView(
        @NotNull String accountLabel,
        @NotNull String listName,
        @NotNull ListKind listKind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int placeCount) {}
