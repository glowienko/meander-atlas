package com.meanderatlas.ingestion.domain;

import com.meanderatlas.shared.GeoPoint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class TimelineParser {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final double ZERO_COORD = 0.0;
    private static final int LAT_GROUP = 1;
    private static final int LNG_GROUP = 2;
    private static final Pattern DEGREE_LAT_LNG_PATTERN =
            Pattern.compile(
                    "^\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*\\u00B0?\\s*,\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*\\u00B0?\\s*$");
    private static final String FIELD_SEMANTIC_SEGMENTS = "semanticSegments";
    private static final String FIELD_START_TIME = "startTime";
    private static final String FIELD_END_TIME = "endTime";
    private static final String FIELD_VISIT = "visit";
    private static final String FIELD_TOP_CANDIDATE = "topCandidate";
    private static final String FIELD_PLACE_ID = "placeId";
    private static final String FIELD_SEMANTIC_TYPE = "semanticType";
    private static final String FIELD_PROBABILITY = "probability";
    private static final String FIELD_PLACE_LOCATION = "placeLocation";
    private static final String FIELD_LAT_LNG = "latLng";
    private static final String NULL_PATH_MESSAGE = "jsonFile must not be null";
    private static final String NULL_SOURCE_MESSAGE = "sourceFile must not be null";
    private static final String NULL_JSON_MESSAGE = "json must not be null";

    private TimelineParser() {}

    public static List<ParsedTimelineVisit> parse(Path jsonFile) throws IOException {
        Objects.requireNonNull(jsonFile, NULL_PATH_MESSAGE);
        String json = Files.readString(jsonFile, StandardCharsets.UTF_8);
        return parse(jsonFile.toString(), json);
    }

    public static List<ParsedTimelineVisit> parse(String sourceFile, String json) {
        Objects.requireNonNull(sourceFile, NULL_SOURCE_MESSAGE);
        Objects.requireNonNull(json, NULL_JSON_MESSAGE);

        JsonNode root = JSON_MAPPER.readTree(TextNormalizer.stripBom(json));
        JsonNode segments = root.path(FIELD_SEMANTIC_SEGMENTS);
        if (!segments.isArray()) {
            return List.of();
        }

        List<ParsedTimelineVisit> visits = new ArrayList<>();
        for (JsonNode segment : segments) {
            JsonNode visit = segment.path(FIELD_VISIT);
            if (!visit.isObject()) {
                continue;
            }
            Instant startTime = parseInstant(textOrNull(segment.path(FIELD_START_TIME)));
            Instant endTime = parseInstant(textOrNull(segment.path(FIELD_END_TIME)));
            if (startTime == null || endTime == null) {
                continue;
            }
            JsonNode topCandidate = visit.path(FIELD_TOP_CANDIDATE);
            String rawLatLng =
                    textOrNull(topCandidate.path(FIELD_PLACE_LOCATION).path(FIELD_LAT_LNG));
            GeoPoint coords = parseDegreeLatLng(rawLatLng).orElse(null);
            if (coords == null) {
                continue;
            }
            String placeId = textOrNull(topCandidate.path(FIELD_PLACE_ID));
            String semanticType = textOrNull(topCandidate.path(FIELD_SEMANTIC_TYPE));
            Double visitProbability = doubleOrNull(visit.path(FIELD_PROBABILITY));
            Double candidateProbability = doubleOrNull(topCandidate.path(FIELD_PROBABILITY));
            visits.add(
                    new ParsedTimelineVisit(
                            sourceFile,
                            placeId,
                            coords,
                            startTime,
                            endTime,
                            semanticType,
                            visitProbability,
                            candidateProbability));
        }
        return List.copyOf(visits);
    }

    public static Optional<GeoPoint> parseDegreeLatLng(@Nullable String rawLatLng) {
        if (rawLatLng == null || rawLatLng.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = DEGREE_LAT_LNG_PATTERN.matcher(rawLatLng);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            double lat = Double.parseDouble(matcher.group(LAT_GROUP));
            double lng = Double.parseDouble(matcher.group(LNG_GROUP));
            if (lat == ZERO_COORD && lng == ZERO_COORD) {
                return Optional.empty();
            }
            return Optional.of(new GeoPoint(lat, lng));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static @Nullable String textOrNull(JsonNode node) {
        if (!node.isString()) {
            return null;
        }
        return TextNormalizer.trimToNull(node.stringValue());
    }

    private static @Nullable Double doubleOrNull(JsonNode node) {
        if (!node.isNumber()) {
            return null;
        }
        return node.doubleValue();
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
