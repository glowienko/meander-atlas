package com.meanderatlas.ingestion.domain;

import com.meanderatlas.atlas.CategorySource;
import com.meanderatlas.atlas.PlaceCategory;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record PlaceClassification(
        PlaceCategory category, @Nullable String subcategory, CategorySource source) {

    private static final String NULL_CATEGORY_MESSAGE = "category must not be null";
    private static final String NULL_SOURCE_MESSAGE = "source must not be null";

    public PlaceClassification {
        Objects.requireNonNull(category, NULL_CATEGORY_MESSAGE);
        Objects.requireNonNull(source, NULL_SOURCE_MESSAGE);
    }

    public static PlaceClassification heuristic(
            PlaceCategory category, @Nullable String subcategory) {
        return new PlaceClassification(category, subcategory, CategorySource.HEURISTIC);
    }
}
