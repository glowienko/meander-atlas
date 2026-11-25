package com.meanderatlas.atlas.domain.stats;

import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceCategory;
import java.util.List;

public record AtlasStats(
        int totalMappedPlaces,
        int visitedPlaces,
        int unvisitedPlaces,
        int needsTriagePlaces,
        int visitedCountries,
        int totalSavedCountries,
        int visitedAdmin1Regions,
        int totalSavedAdmin1Regions,
        List<CategoryCount> categories,
        List<SubcategoryCount> topSubcategories,
        List<AccountListCount> accountLists) {

    public record CategoryCount(PlaceCategory category, int totalCount, int visitedCount) {}

    public record SubcategoryCount(
            String subcategory, PlaceCategory category, int totalCount, int visitedCount) {}

    public record AccountListCount(
            String accountLabel, String listName, ListKind listKind, int placeCount) {}
}
