package com.meanderatlas.ingestion.domain.classification;

import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.PlaceCategory;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record PlaceClassification(
        PlaceCategory category, @Nullable String subcategory, CategorySource source) {

    public PlaceClassification {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(source, "source must not be null");
    }

    public static PlaceClassification heuristic(
            PlaceCategory category, @Nullable String subcategory) {
        return new PlaceClassification(category, subcategory, CategorySource.HEURISTIC);
    }
}
