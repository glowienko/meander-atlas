package com.meanderatlas.atlas.domain.stats;

import com.meanderatlas.atlas.domain.RegionLevel;
import java.util.UUID;

public record RegionSummary(
        UUID id,
        RegionLevel level,
        String code,
        String countryCode,
        String name,
        int totalPlaces,
        int visitedPlaces,
        int unvisitedPlaces,
        int needsTriagePlaces,
        double minLng,
        double minLat,
        double maxLng,
        double maxLat) {

    public double completionRatio() {
        return totalPlaces == 0 ? 0.0 : (double) visitedPlaces / totalPlaces;
    }
}
