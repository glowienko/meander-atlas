package com.meanderatlas.ingestion.domain;

import com.meanderatlas.atlas.ListKind;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.VisitSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class TakeoutGeoJsonParser {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final double ZERO_COORD = 0.0;
    private static final int LNG_INDEX = 0;
    private static final int LAT_INDEX = 1;
    private static final int MIN_COORDINATE_ELEMENTS = 2;
    private static final int MIN_STAR_RATING = 1;
    private static final int MAX_STAR_RATING = 5;
    private static final String JSON_EXTENSION = ".json";
    private static final String FIELD_FEATURES = "features";
    private static final String FIELD_GEOMETRY = "geometry";
    private static final String FIELD_COORDINATES = "coordinates";
    private static final String FIELD_PROPERTIES = "properties";
    private static final String FIELD_LOCATION = "location";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_TITLE = "Title";
    private static final String FIELD_ADDRESS = "address";
    private static final String FIELD_ADDRESS_ALT = "Address";
    private static final String FIELD_COUNTRY_CODE = "country_code";
    private static final String FIELD_COUNTRY_CODE_ALT = "Country Code";
    private static final String FIELD_GOOGLE_MAPS_URL = "google_maps_url";
    private static final String FIELD_GOOGLE_MAPS_URL_ALT = "Google Maps URL";
    private static final String FIELD_DATE = "date";
    private static final String FIELD_PUBLISHED = "Published";
    private static final String FIELD_COMMENT = "Comment";
    private static final String FIELD_COMMENT_LOWER = "comment";
    private static final String FIELD_FIVE_STAR_RATING = "five_star_rating_published";
    private static final String FIELD_REVIEW_TEXT = "review_text_published";
    private static final String NULL_PATH_MESSAGE = "jsonFile must not be null";
    private static final String NULL_SOURCE_MESSAGE = "sourceFile must not be null";
    private static final String NULL_JSON_MESSAGE = "json must not be null";
    private static final String NULL_FILENAME_MESSAGE = "fileName must not be null";
    private static final Set<String> SAVED_PLACES_FILENAMES =
            Set.of("zapisane miejsca", "saved places");
    private static final Set<String> REVIEWS_FILENAMES = Set.of("opinie", "reviews");

    private TakeoutGeoJsonParser() {}

    public static List<ParsedSavedPlace> parseSavedPlaces(Path jsonFile) throws IOException {
        Objects.requireNonNull(jsonFile, NULL_PATH_MESSAGE);
        String json = Files.readString(jsonFile, StandardCharsets.UTF_8);
        return parseSavedPlaces(jsonFile.toString(), json);
    }

    public static List<ParsedSavedPlace> parseSavedPlaces(String sourceFile, String json) {
        Objects.requireNonNull(sourceFile, NULL_SOURCE_MESSAGE);
        Objects.requireNonNull(json, NULL_JSON_MESSAGE);

        JsonNode root = JSON_MAPPER.readTree(TextNormalizer.stripBom(json));
        JsonNode features = root.path(FIELD_FEATURES);
        if (!features.isArray()) {
            return List.of();
        }

        List<ParsedSavedPlace> results = new ArrayList<>();
        for (JsonNode feature : features) {
            GeoPoint coords = extractValidPoint(feature.path(FIELD_GEOMETRY));
            if (coords == null) {
                continue;
            }
            JsonNode props = feature.path(FIELD_PROPERTIES);
            JsonNode location = props.path(FIELD_LOCATION);
            String name = firstText(location, FIELD_NAME, props, FIELD_TITLE, FIELD_NAME);
            if (name == null) {
                continue;
            }
            String url = firstText(props, FIELD_GOOGLE_MAPS_URL, FIELD_GOOGLE_MAPS_URL_ALT);
            GoogleCid cid = GoogleCidDecoder.decodeFromUrl(url).orElse(null);
            String countryCode =
                    firstText(location, FIELD_COUNTRY_CODE, props, FIELD_COUNTRY_CODE_ALT);
            String address = firstText(location, FIELD_ADDRESS, props, FIELD_ADDRESS_ALT);
            Instant savedAt = parseInstant(firstText(props, FIELD_DATE, FIELD_PUBLISHED));
            String comment = firstText(props, FIELD_COMMENT, FIELD_COMMENT_LOWER);
            results.add(
                    new ParsedSavedPlace(
                            sourceFile,
                            name,
                            coords,
                            url,
                            cid,
                            countryCode,
                            address,
                            savedAt,
                            comment,
                            ListKind.STARRED));
        }
        return List.copyOf(results);
    }

    public static List<ParsedReview> parseReviews(Path jsonFile) throws IOException {
        Objects.requireNonNull(jsonFile, NULL_PATH_MESSAGE);
        String json = Files.readString(jsonFile, StandardCharsets.UTF_8);
        return parseReviews(jsonFile.toString(), json);
    }

    public static List<ParsedReview> parseReviews(String sourceFile, String json) {
        Objects.requireNonNull(sourceFile, NULL_SOURCE_MESSAGE);
        Objects.requireNonNull(json, NULL_JSON_MESSAGE);

        JsonNode root = JSON_MAPPER.readTree(TextNormalizer.stripBom(json));
        JsonNode features = root.path(FIELD_FEATURES);
        if (!features.isArray()) {
            return List.of();
        }

        List<ParsedReview> results = new ArrayList<>();
        for (JsonNode feature : features) {
            GeoPoint coords = extractValidPoint(feature.path(FIELD_GEOMETRY));
            JsonNode props = feature.path(FIELD_PROPERTIES);
            JsonNode location = props.path(FIELD_LOCATION);
            String name = firstText(location, FIELD_NAME, props, FIELD_TITLE, FIELD_NAME);
            String url = firstText(props, FIELD_GOOGLE_MAPS_URL, FIELD_GOOGLE_MAPS_URL_ALT);
            GoogleCid cid = GoogleCidDecoder.decodeFromUrl(url).orElse(null);
            if (coords == null && name == null && cid == null) {
                continue;
            }
            String countryCode =
                    firstText(location, FIELD_COUNTRY_CODE, props, FIELD_COUNTRY_CODE_ALT);
            String address = firstText(location, FIELD_ADDRESS, props, FIELD_ADDRESS_ALT);
            Instant reviewedAt = parseInstant(firstText(props, FIELD_DATE, FIELD_PUBLISHED));
            Integer rating = extractRating(props.path(FIELD_FIVE_STAR_RATING));
            String reviewText = textOrNull(props.path(FIELD_REVIEW_TEXT));
            results.add(
                    new ParsedReview(
                            sourceFile,
                            name,
                            coords,
                            url,
                            cid,
                            countryCode,
                            address,
                            reviewedAt,
                            rating,
                            reviewText,
                            VisitSource.REVIEW));
        }
        return List.copyOf(results);
    }

    public static boolean isSavedPlacesFile(String fileName) {
        Objects.requireNonNull(fileName, NULL_FILENAME_MESSAGE);
        return SAVED_PLACES_FILENAMES.contains(canonicalJsonBaseName(fileName));
    }

    public static boolean isReviewsFile(String fileName) {
        Objects.requireNonNull(fileName, NULL_FILENAME_MESSAGE);
        return REVIEWS_FILENAMES.contains(canonicalJsonBaseName(fileName));
    }

    private static String canonicalJsonBaseName(String fileName) {
        String normalizedPath = fileName.replace('\\', '/');
        int lastSlash = normalizedPath.lastIndexOf('/');
        String base = lastSlash >= 0 ? normalizedPath.substring(lastSlash + 1) : normalizedPath;
        if (base.toLowerCase(Locale.ROOT).endsWith(JSON_EXTENSION)) {
            base = base.substring(0, base.length() - JSON_EXTENSION.length());
        }
        return TextNormalizer.normalizeFolded(base);
    }

    private static @Nullable GeoPoint extractValidPoint(JsonNode geometry) {
        JsonNode coords = geometry.path(FIELD_COORDINATES);
        if (!coords.isArray() || coords.size() < MIN_COORDINATE_ELEMENTS) {
            return null;
        }
        JsonNode lngNode = coords.get(LNG_INDEX);
        JsonNode latNode = coords.get(LAT_INDEX);
        if (lngNode == null || latNode == null || !lngNode.isNumber() || !latNode.isNumber()) {
            return null;
        }
        double lng = lngNode.doubleValue();
        double lat = latNode.doubleValue();
        if (lat == ZERO_COORD && lng == ZERO_COORD) {
            return null;
        }
        try {
            return new GeoPoint(lat, lng);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static @Nullable Integer extractRating(JsonNode node) {
        if (!node.isIntegralNumber()) {
            return null;
        }
        int value = node.intValue();
        if (value < MIN_STAR_RATING || value > MAX_STAR_RATING) {
            return null;
        }
        return value;
    }

    private static @Nullable String firstText(
            JsonNode primary, String primaryKey, JsonNode fallback, String... fallbackKeys) {
        String value = textOrNull(primary.path(primaryKey));
        if (value != null) {
            return value;
        }
        return firstText(fallback, fallbackKeys);
    }

    private static @Nullable String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            String value = textOrNull(node.path(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static @Nullable String textOrNull(JsonNode node) {
        if (!node.isString()) {
            return null;
        }
        return TextNormalizer.trimToNull(node.stringValue());
    }

    private static @Nullable Instant parseInstant(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
