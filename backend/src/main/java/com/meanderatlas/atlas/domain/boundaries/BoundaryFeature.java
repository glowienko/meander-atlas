package com.meanderatlas.atlas.domain.boundaries;

import com.meanderatlas.atlas.domain.RegionLevel;
import java.util.Objects;

public record BoundaryFeature(
        RegionLevel level, String code, String countryCode, String name, String geometryJson) {

    public BoundaryFeature {
        Objects.requireNonNull(level, "level must not be null");
        if (Objects.requireNonNull(code, "code must not be null").isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (Objects.requireNonNull(countryCode, "countryCode must not be null").isBlank()) {
            throw new IllegalArgumentException("countryCode must not be blank");
        }
        if (Objects.requireNonNull(name, "name must not be null").isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (Objects.requireNonNull(geometryJson, "geometryJson must not be null").isBlank()) {
            throw new IllegalArgumentException("geometryJson must not be blank");
        }
    }
}
