package com.meanderatlas.ingestion.domain.timeline;

import com.meanderatlas.ingestion.domain.TextNormalizer;
import com.meanderatlas.shared.GeoPoint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
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
    private static final String FIELD_PROBABILITY = "probability";

    private TimelineParser() {}

    public static List<ParsedTimelineVisit> parse(Path jsonFile) throws IOException {
        Objects.requireNonNull(jsonFile, "jsonFile must not be null");
        return parse(jsonFile.toString(), Files.readString(jsonFile, StandardCharsets.UTF_8));
    }

    public static List<ParsedTimelineVisit> parse(String sourceFile, String json) {
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        Objects.requireNonNull(json, "json must not be null");

        JsonNode segments =
                JSON_MAPPER.readTree(TextNormalizer.stripBom(json)).path("semanticSegments");
        if (!segments.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(segments.spliterator(), false)
                .flatMap(segment -> parseVisitSegment(sourceFile, segment).stream())
                .toList();
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

    private static Optional<ParsedTimelineVisit> parseVisitSegment(
            String sourceFile, JsonNode segment) {
        JsonNode visit = segment.path("visit");
        if (!visit.isObject()) {
            return Optional.empty();
        }
        Instant startTime = parseInstant(textOrNull(segment.path("startTime")));
        Instant endTime = parseInstant(textOrNull(segment.path("endTime")));
        if (startTime == null || endTime == null) {
            return Optional.empty();
        }
        JsonNode topCandidate = visit.path("topCandidate");
        String rawLatLng = textOrNull(topCandidate.path("placeLocation").path("latLng"));
        return parseDegreeLatLng(rawLatLng)
                .map(coords ->
                        new ParsedTimelineVisit(
                                sourceFile,
                                textOrNull(topCandidate.path("placeId")),
                                coords,
                                startTime,
                                endTime,
                                textOrNull(topCandidate.path("semanticType")),
                                doubleOrNull(visit.path(FIELD_PROBABILITY)),
                                doubleOrNull(topCandidate.path(FIELD_PROBABILITY))));
    }

    private static @Nullable String textOrNull(JsonNode node) {
        return node.isString() ? TextNormalizer.trimToNull(node.stringValue()) : null;
    }

    private static @Nullable Double doubleOrNull(JsonNode node) {
        return node.isNumber() ? node.doubleValue() : null;
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
