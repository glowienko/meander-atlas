package com.meanderatlas.ingestion.adapter.http;

import com.meanderatlas.ingestion.domain.TextNormalizer;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.AnchoredEnrichmentTarget;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.OpenPoiCandidate;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.GeoPoint;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OverpassPoiClient {

    private static final int EXACT_RADIUS_METERS = 600;
    private static final int APPROX_RADIUS_METERS = 2500;
    private static final double SPATIAL_CELL_DEGREES = 2.0;
    private static final String POI_KEY_REGEX =
            "^(tourism|amenity|natural|historic|leisure|shop|craft|man_made|waterway|boundary|railway|aeroway)$";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final RestClient restClient;
    private final EnrichmentProperties properties;

    OverpassPoiClient(RestClient.Builder builder, EnrichmentProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    List<List<AnchoredEnrichmentTarget>> partitionIntoBatches(
            List<AnchoredEnrichmentTarget> targets) {
        if (targets.isEmpty()) {
            return List.of();
        }
        int batchSize = Math.max(1, properties.batchSize());
        Map<SpatialCell, List<AnchoredEnrichmentTarget>> byCell =
                targets.stream().collect(Collectors.groupingBy(OverpassPoiClient::spatialCellOf));
        return byCell.entrySet().stream()
                .sorted(Map.Entry.<SpatialCell, List<AnchoredEnrichmentTarget>>comparingByKey())
                .flatMap(entry -> sliceIntoChunks(entry.getValue(), batchSize))
                .toList();
    }

    List<OpenPoiCandidate> fetchBatch(List<AnchoredEnrichmentTarget> batch) {
        if (batch.isEmpty()) {
            return List.of();
        }
        String query = buildOverpassQuery(batch);
        String encodedBody = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        String rawResponse =
                restClient
                        .post()
                        .uri(properties.overpassUrl())
                        .header(HttpHeaders.USER_AGENT, properties.wikimediaUserAgent())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(encodedBody)
                        .retrieve()
                        .body(String.class);
        return parseResponse(rawResponse);
    }

    static String buildOverpassQuery(List<AnchoredEnrichmentTarget> batch) {
        String clauses =
                batch.stream()
                        .map(OverpassPoiClient::buildTargetClause)
                        .collect(Collectors.joining());
        return "[out:json][timeout:25];(" + clauses + ");out center tags;";
    }

    static List<OpenPoiCandidate> parseResponse(@Nullable String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return List.of();
        }
        JsonNode root;
        try {
            root = JSON_MAPPER.readTree(rawResponse);
        } catch (JacksonException ex) {
            throw new OverpassTransientException("Malformed Overpass JSON response", ex);
        }
        JsonNode elements = root.path("elements");
        if (!elements.isArray() || elements.isEmpty()) {
            String remark = root.path("remark").asString("");
            String lowerRemark = remark.toLowerCase(Locale.ROOT);
            if (lowerRemark.contains("error") || lowerRemark.contains("timed out")) {
                throw new OverpassTransientException("Overpass remark indicates error: " + remark);
            }
            return List.of();
        }
        return StreamSupport.stream(elements.spliterator(), false)
                .map(OverpassPoiClient::parseElement)
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<OpenPoiCandidate> parseElement(JsonNode element) {
        String type = TextNormalizer.trimToNull(element.path("type").asString(""));
        long id = element.path("id").asLong(-1L);
        if (type == null || id <= 0) {
            return Optional.empty();
        }
        GeoPoint coordinates = extractCoordinates(element);
        if (coordinates == null) {
            return Optional.empty();
        }
        Map<String, String> tags = extractTags(element.path("tags"));
        String name =
                firstNonBlank(
                        tags.get("name"),
                        tags.get("name:en"),
                        tags.get("int_name"),
                        tags.get("alt_name"),
                        tags.get("short_name"));
        if (name == null) {
            return Optional.empty();
        }
        String cityName =
                firstNonBlank(
                        tags.get("addr:city"),
                        tags.get("addr:town"),
                        tags.get("addr:village"),
                        tags.get("is_in:city"));
        String countryCode =
                Optional.ofNullable(tags.get("addr:country"))
                        .map(TextNormalizer::trimToNull)
                        .map(code -> code.toUpperCase(Locale.ROOT))
                        .orElse(null);
        String openingHours = TextNormalizer.trimToNull(tags.get("opening_hours"));
        return Optional.of(
                new OpenPoiCandidate(
                        type + "/" + id,
                        name,
                        coordinates,
                        tags,
                        cityName,
                        countryCode,
                        openingHours));
    }

    private static @Nullable GeoPoint extractCoordinates(JsonNode element) {
        JsonNode latNode = element.get("lat");
        JsonNode lonNode = element.get("lon");
        if (latNode == null || lonNode == null || !latNode.isNumber() || !lonNode.isNumber()) {
            JsonNode center = element.get("center");
            if (center == null || !center.isObject()) {
                return null;
            }
            latNode = center.get("lat");
            lonNode = center.get("lon");
        }
        if (latNode == null || lonNode == null || !latNode.isNumber() || !lonNode.isNumber()) {
            return null;
        }
        double lat = latNode.doubleValue();
        double lon = lonNode.doubleValue();
        if (!Double.isFinite(lat)
                || !Double.isFinite(lon)
                || lat < -90.0
                || lat > 90.0
                || lon < -180.0
                || lon > 180.0) {
            return null;
        }
        return new GeoPoint(lat, lon);
    }

    private static Map<String, String> extractTags(JsonNode tagsNode) {
        if (!tagsNode.isObject()) {
            return Map.of();
        }
        return tagsNode.properties().stream()
                .filter(entry -> entry.getValue().isString())
                .map(entry ->
                        Map.entry(
                                entry.getKey(),
                                Objects.requireNonNullElse(entry.getValue().stringValue(), "")))
                .filter(entry -> !entry.getValue().isBlank())
                .collect(
                        Collectors.toUnmodifiableMap(
                                Map.Entry::getKey, Map.Entry::getValue, (a, _) -> a));
    }

    private static String buildTargetClause(AnchoredEnrichmentTarget target) {
        int radius =
                target.coordSource() == CoordSource.TAKEOUT
                                || target.coordSource() == CoordSource.TIMELINE
                        ? EXACT_RADIUS_METERS
                        : APPROX_RADIUS_METERS;
        return String.format(
                Locale.ROOT,
                "nwr(around:%d,%.6f,%.6f)[\"name\"][~\"%s\"~\".\"];",
                radius,
                target.coordinates().latitude(),
                target.coordinates().longitude(),
                POI_KEY_REGEX);
    }

    private static SpatialCell spatialCellOf(AnchoredEnrichmentTarget target) {
        int latBucket = (int) Math.floor(target.coordinates().latitude() / SPATIAL_CELL_DEGREES);
        int lngBucket = (int) Math.floor(target.coordinates().longitude() / SPATIAL_CELL_DEGREES);
        return new SpatialCell(latBucket, lngBucket);
    }

    private static Stream<List<AnchoredEnrichmentTarget>> sliceIntoChunks(
            List<AnchoredEnrichmentTarget> items, int chunkSize) {
        return IntStream.iterate(0, i -> i < items.size(), i -> i + chunkSize)
                .mapToObj(start -> items.subList(start, Math.min(start + chunkSize, items.size())));
    }

    private static @Nullable String firstNonBlank(@Nullable String... candidates) {
        return Stream.of(candidates)
                .map(TextNormalizer::trimToNull)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private record SpatialCell(int latBucket, int lngBucket) implements Comparable<SpatialCell> {

        @Override
        public int compareTo(SpatialCell other) {
            int byLat = Integer.compare(latBucket, other.latBucket);
            return byLat != 0 ? byLat : Integer.compare(lngBucket, other.lngBucket);
        }
    }

    static final class OverpassTransientException extends RuntimeException {

        OverpassTransientException(String message) {
            super(message);
        }

        OverpassTransientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
