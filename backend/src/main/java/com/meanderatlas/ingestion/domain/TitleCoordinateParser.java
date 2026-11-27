package com.meanderatlas.ingestion.domain;

import com.meanderatlas.shared.GeoPoint;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class TitleCoordinateParser {

    private static final double MINUTES_PER_DEGREE = 60.0;
    private static final double SECONDS_PER_DEGREE = 3600.0;
    private static final double NULL_ISLAND_EPSILON = 1e-6;
    private static final String DEGREE_CHARS = "[\\u00B0\\u00BA\\u02DA]";
    private static final String MINUTE_CHARS = "['\\u2032\\u2019\\u2018\\u02B9]";
    private static final String SECOND_CHARS =
            "(?:\"|\\u2033|\\u201D|\\u201C|\\u02BA|''|\\u2032\\u2032|\\u2019\\u2019)";
    private static final String NUMBER_TOKEN = "(\\d+(?:[.,]\\d+)?)";
    private static final String DMS_BODY =
            NUMBER_TOKEN
                    + "\\s*"
                    + DEGREE_CHARS
                    + "(?:\\s*"
                    + NUMBER_TOKEN
                    + "\\s*"
                    + MINUTE_CHARS
                    + "(?:\\s*"
                    + NUMBER_TOKEN
                    + "\\s*"
                    + SECOND_CHARS
                    + ")?)?";
    private static final String SEPARATOR = "(?:\\s*[,;]\\s*|\\s+)";
    private static final Pattern PREFIX_HEMISPHERE_PATTERN =
            Pattern.compile(
                    "^([NSEWnsew])\\s*"
                            + DMS_BODY
                            + SEPARATOR
                            + "([NSEWnsew])\\s*"
                            + DMS_BODY
                            + "$");
    private static final Pattern SUFFIX_HEMISPHERE_PATTERN =
            Pattern.compile(
                    "^"
                            + DMS_BODY
                            + "\\s*([NSEWnsew])"
                            + SEPARATOR
                            + DMS_BODY
                            + "\\s*([NSEWnsew])$");
    private static final Pattern DECIMAL_COORDS_PATTERN =
            Pattern.compile(
                    "^([+-]?\\d{1,2}\\.\\d+)\\s*\\u00B0?\\s*,\\s*([+-]?\\d{1,3}\\.\\d+)\\s*\\u00B0?$");

    private TitleCoordinateParser() {}

    public static Optional<GeoPoint> parse(@Nullable String rawTitle) {
        if (rawTitle == null) {
            return Optional.empty();
        }
        String normalized = TextNormalizer.normalizeSpaces(rawTitle);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        Matcher prefixMatcher = PREFIX_HEMISPHERE_PATTERN.matcher(normalized);
        if (prefixMatcher.matches()) {
            Optional<GeoPoint> parsed =
                    parseHemisphereValues(
                            prefixMatcher.group(1),
                            prefixMatcher.group(2),
                            prefixMatcher.group(3),
                            prefixMatcher.group(4),
                            prefixMatcher.group(5),
                            prefixMatcher.group(6),
                            prefixMatcher.group(7),
                            prefixMatcher.group(8));
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        Matcher suffixMatcher = SUFFIX_HEMISPHERE_PATTERN.matcher(normalized);
        if (suffixMatcher.matches()) {
            Optional<GeoPoint> parsed =
                    parseHemisphereValues(
                            suffixMatcher.group(4),
                            suffixMatcher.group(1),
                            suffixMatcher.group(2),
                            suffixMatcher.group(3),
                            suffixMatcher.group(8),
                            suffixMatcher.group(5),
                            suffixMatcher.group(6),
                            suffixMatcher.group(7));
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        Matcher decimalMatcher = DECIMAL_COORDS_PATTERN.matcher(normalized);
        if (decimalMatcher.matches()) {
            return parseDecimalPair(decimalMatcher.group(1), decimalMatcher.group(2));
        }
        return Optional.empty();
    }

    private static Optional<GeoPoint> parseHemisphereValues(
            String rawLatHemi,
            @Nullable String latDeg,
            @Nullable String latMin,
            @Nullable String latSec,
            String rawLngHemi,
            @Nullable String lngDeg,
            @Nullable String lngMin,
            @Nullable String lngSec) {
        String latHemi = rawLatHemi.toUpperCase(Locale.ROOT);
        String lngHemi = rawLngHemi.toUpperCase(Locale.ROOT);
        if ((!"N".equals(latHemi) && !"S".equals(latHemi))
                || (!"E".equals(lngHemi) && !"W".equals(lngHemi))) {
            return Optional.empty();
        }
        Double absLat = parseDmsMagnitude(latDeg, latMin, latSec);
        Double absLng = parseDmsMagnitude(lngDeg, lngMin, lngSec);
        if (absLat == null || absLng == null) {
            return Optional.empty();
        }
        double lat = "S".equals(latHemi) ? -absLat : absLat;
        double lng = "W".equals(lngHemi) ? -absLng : absLng;
        return toValidGeoPoint(lat, lng);
    }

    private static @Nullable Double parseDmsMagnitude(
            @Nullable String degRaw, @Nullable String minRaw, @Nullable String secRaw) {
        if (degRaw == null) {
            return null;
        }
        if (minRaw != null && hasFraction(degRaw)) {
            return null;
        }
        if (secRaw != null && (minRaw == null || hasFraction(minRaw))) {
            return null;
        }
        Double deg = parseDouble(degRaw);
        Double min = minRaw == null ? Double.valueOf(0.0) : parseDouble(minRaw);
        Double sec = secRaw == null ? Double.valueOf(0.0) : parseDouble(secRaw);
        if (deg == null || min == null || sec == null) {
            return null;
        }
        if (min < 0.0 || min >= MINUTES_PER_DEGREE || sec < 0.0 || sec >= MINUTES_PER_DEGREE) {
            return null;
        }
        return deg + (min / MINUTES_PER_DEGREE) + (sec / SECONDS_PER_DEGREE);
    }

    private static Optional<GeoPoint> parseDecimalPair(String latRaw, String lngRaw) {
        Double lat = parseDouble(latRaw);
        Double lng = parseDouble(lngRaw);
        if (lat == null || lng == null) {
            return Optional.empty();
        }
        return toValidGeoPoint(lat, lng);
    }

    private static Optional<GeoPoint> toValidGeoPoint(double lat, double lng) {
        if (!Double.isFinite(lat)
                || !Double.isFinite(lng)
                || Math.abs(lat) > GeoPoint.MAX_LATITUDE
                || Math.abs(lng) > GeoPoint.MAX_LONGITUDE) {
            return Optional.empty();
        }
        if (Math.abs(lat) < NULL_ISLAND_EPSILON && Math.abs(lng) < NULL_ISLAND_EPSILON) {
            return Optional.empty();
        }
        double cleanLat = lat == 0.0 ? 0.0 : lat;
        double cleanLng = lng == 0.0 ? 0.0 : lng;
        return Optional.of(new GeoPoint(cleanLat, cleanLng));
    }

    private static boolean hasFraction(String token) {
        return token.indexOf('.') >= 0 || token.indexOf(',') >= 0;
    }

    private static @Nullable Double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
