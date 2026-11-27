package com.meanderatlas.atlas;

import com.meanderatlas.shared.PlaceId;

public record PlaceEnrichmentResultView(
        PlaceId placeId, boolean coordsUpgraded, boolean categoryUpgraded, boolean photoAdded) {}
