package com.meanderatlas.atlas;

public record BoundarySeedResultView(
        int countriesUpserted, int admin1Upserted, int placesTagged, boolean skipped) {}
