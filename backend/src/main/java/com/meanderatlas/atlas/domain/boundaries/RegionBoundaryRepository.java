package com.meanderatlas.atlas.domain.boundaries;

import com.meanderatlas.atlas.domain.RegionLevel;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface RegionBoundaryRepository {

    int upsertBoundaries(List<BoundaryFeature> features, double simplifyTolerance);

    long countByLevel(RegionLevel level);

    int assignRegionsToUntaggedPlaces();

    byte[] fetchRegionMvtTile(RegionLevel level, int z, int x, int y, @Nullable String countryCode);
}
