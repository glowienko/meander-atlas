package com.meanderatlas.atlas.domain.places;

public record BoundingBox(double minLng, double minLat, double maxLng, double maxLat) {

    private static final double MIN_LNG = -180.0;
    private static final double MAX_LNG = 180.0;
    private static final double MIN_LAT = -90.0;
    private static final double MAX_LAT = 90.0;

    public BoundingBox {
        if (!Double.isFinite(minLng)
                || !Double.isFinite(minLat)
                || !Double.isFinite(maxLng)
                || !Double.isFinite(maxLat)) {
            throw new IllegalArgumentException("Bounding box coordinates must be finite");
        }
        if (minLng < MIN_LNG || maxLng > MAX_LNG || minLng > maxLng) {
            throw new IllegalArgumentException(
                    "Invalid longitude bounds: [" + minLng + ", " + maxLng + "]");
        }
        if (minLat < MIN_LAT || maxLat > MAX_LAT || minLat > maxLat) {
            throw new IllegalArgumentException(
                    "Invalid latitude bounds: [" + minLat + ", " + maxLat + "]");
        }
    }
}
