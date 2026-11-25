package com.meanderatlas.atlas.domain.places;

public record BoundingBox(double minLng, double minLat, double maxLng, double maxLat) {

    private static final double MIN_LNG = -180.0;
    private static final double MAX_LNG = 180.0;
    private static final double FULL_WORLD_SPAN = 360.0;
    private static final double MIN_LAT = -90.0;
    private static final double MAX_LAT = 90.0;

    public BoundingBox {
        if (!Double.isFinite(minLng)
                || !Double.isFinite(minLat)
                || !Double.isFinite(maxLng)
                || !Double.isFinite(maxLat)) {
            throw new IllegalArgumentException("Bounding box coordinates must be finite");
        }
        if (minLat < MIN_LAT || maxLat > MAX_LAT || minLat > maxLat) {
            throw new IllegalArgumentException(
                    "Invalid latitude bounds: [" + minLat + ", " + maxLat + "]");
        }
        if (maxLng - minLng >= FULL_WORLD_SPAN) {
            minLng = MIN_LNG;
            maxLng = MAX_LNG;
        } else if (minLng < MIN_LNG || maxLng > MAX_LNG) {
            minLng = wrapLongitude(minLng);
            maxLng = normalizeMaxLongitude(minLng, wrapLongitude(maxLng));
        }
    }

    public boolean crossesAntimeridian() {
        return minLng > maxLng;
    }

    private static double normalizeMaxLongitude(double wrappedMin, double wrappedMax) {
        return wrappedMax == MIN_LNG && wrappedMin > MIN_LNG ? MAX_LNG : wrappedMax;
    }

    private static double wrapLongitude(double lng) {
        return ((((lng + MAX_LNG) % FULL_WORLD_SPAN) + FULL_WORLD_SPAN) % FULL_WORLD_SPAN)
                - MAX_LNG;
    }
}
