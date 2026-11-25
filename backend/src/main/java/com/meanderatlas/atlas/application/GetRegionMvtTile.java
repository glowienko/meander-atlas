package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.RegionScope;
import com.meanderatlas.atlas.domain.RegionLevel;
import com.meanderatlas.atlas.domain.boundaries.RegionBoundaryRepository;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class GetRegionMvtTile {

    private static final int MIN_ZOOM = 0;
    private static final int MAX_ZOOM = 14;

    private final RegionBoundaryRepository regionBoundaryRepository;

    public GetRegionMvtTile(RegionBoundaryRepository regionBoundaryRepository) {
        this.regionBoundaryRepository = regionBoundaryRepository;
    }

    public byte[] execute(RegionScope scope, int z, int x, int y, @Nullable String countryCode) {
        Objects.requireNonNull(scope, "scope must not be null");
        if (z < MIN_ZOOM || z > MAX_ZOOM) {
            throw new IllegalArgumentException("Tile zoom z must be between 0 and 14");
        }
        int gridSize = 1 << z;
        if (x < 0 || x >= gridSize || y < 0 || y >= gridSize) {
            throw new IllegalArgumentException(
                    "Tile x/y out of bounds for zoom " + z + ": " + x + "," + y);
        }
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        RegionLevel level =
                switch (scope) {
                    case COUNTRY -> RegionLevel.COUNTRY;
                    case ADMIN1 -> RegionLevel.ADMIN1;
                };
        return regionBoundaryRepository.fetchRegionMvtTile(level, z, x, y, normalizedCountryCode);
    }

    private static @Nullable String normalizeCountryCode(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
