package com.meanderatlas.atlas;

import com.meanderatlas.atlas.application.GetAtlasHealth;
import com.meanderatlas.atlas.application.GetRegionMvtTile;
import com.meanderatlas.atlas.application.SeedRegionBoundaries;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class AtlasFacade {

    private final GetAtlasHealth getAtlasHealth;
    private final SeedRegionBoundaries seedRegionBoundaries;
    private final GetRegionMvtTile getRegionMvtTile;

    public AtlasFacade(
            GetAtlasHealth getAtlasHealth,
            SeedRegionBoundaries seedRegionBoundaries,
            GetRegionMvtTile getRegionMvtTile) {
        this.getAtlasHealth = getAtlasHealth;
        this.seedRegionBoundaries = seedRegionBoundaries;
        this.getRegionMvtTile = getRegionMvtTile;
    }

    public AtlasHealthView health() {
        return getAtlasHealth.execute();
    }

    public BoundarySeedResultView seedBoundaries(boolean force) {
        return seedRegionBoundaries.execute(force);
    }

    public byte[] regionMvtTile(
            RegionScope scope, int z, int x, int y, @Nullable String countryCode) {
        return getRegionMvtTile.execute(scope, z, x, y, countryCode);
    }
}
