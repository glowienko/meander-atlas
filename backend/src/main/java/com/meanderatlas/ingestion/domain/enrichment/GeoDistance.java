package com.meanderatlas.ingestion.domain.enrichment;

import com.meanderatlas.shared.GeoPoint;

public final class GeoDistance {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private GeoDistance() {}

    public static double metersBetween(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double deltaLat = lat2 - lat1;
        double deltaLng = Math.toRadians(b.longitude() - a.longitude());
        double sinLat = Math.sin(deltaLat / 2.0);
        double sinLng = Math.sin(deltaLng / 2.0);
        double haversine = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLng * sinLng;
        double clamped = Math.min(1.0, Math.max(0.0, haversine));
        return 2.0 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(clamped));
    }
}
