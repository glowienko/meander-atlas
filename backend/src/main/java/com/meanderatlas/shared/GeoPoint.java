package com.meanderatlas.shared;

public record GeoPoint(double latitude, double longitude) {

    public static final double MIN_LATITUDE = -90.0;
    public static final double MAX_LATITUDE = 90.0;
    public static final double MIN_LONGITUDE = -180.0;
    public static final double MAX_LONGITUDE = 180.0;

    private static final String INVALID_LATITUDE_MESSAGE = "Latitude must be between -90 and 90";
    private static final String INVALID_LONGITUDE_MESSAGE =
            "Longitude must be between -180 and 180";

    public GeoPoint {
        if (!Double.isFinite(latitude) || latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) {
            throw new IllegalArgumentException(INVALID_LATITUDE_MESSAGE);
        }
        if (!Double.isFinite(longitude) || longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            throw new IllegalArgumentException(INVALID_LONGITUDE_MESSAGE);
        }
    }
}
