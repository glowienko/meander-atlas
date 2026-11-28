package com.meanderatlas.atlas;

import com.meanderatlas.shared.PlaceCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record UpdatePlaceCategoryRequest(
        @NotNull PlaceCategory category, @Nullable @Size(max = 120) String subcategory) {}
