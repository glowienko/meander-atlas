package com.meanderatlas.atlas;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RegionDossierView(
        @NotNull RegionSummaryView region,
        @NotNull List<RegionSummaryView> childRegions,
        @NotNull List<AtlasPlaceView> places) {}
