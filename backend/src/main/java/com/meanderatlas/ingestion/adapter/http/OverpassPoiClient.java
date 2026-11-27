package com.meanderatlas.ingestion.adapter.http;

import com.meanderatlas.ingestion.adapter.http.OpenDataHttp.OpenDataResponseException;
import com.meanderatlas.ingestion.application.EnrichmentProperties;
import com.meanderatlas.ingestion.domain.TextNormalizer;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.AnchoredEnrichmentTarget;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.OpenPoiCandidate;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiMatcher;
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
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

class OverpassPoiClient {

    private static final int MAX_OVERPASS_CLAUSES_PER_BATCH = 10;
    private static final double SPATIAL_CELL_DEGREES = 2.0;
    private static final String POI_KEY_REGEX =
            "^(tourism|amenity|natural|historic|leisure|shop|craft|man_made|waterway|boundary|railway|aeroway)$";

    private final OpenDataHttp http;
    private final EnrichmentProperties properties;

    OverpassPoiClient(RestClient.Builder builder, EnrichmentProperties properties) {
        this.http = new OpenDataHttp(builder, properties.wikimediaUserAgent());
        this.properties = properties;
    }

    List<List<AnchoredEnrichmentTarget>> partitionIntoBatches(
            List<AnchoredEnrichmentTarget> targets) {
        if (targets.isEmpty()) {
            return List.of();
        }
        int batchSize =
                Math.min(Math.max(1, properties.batchSize()), MAX_OVERPASS_CLAUSES_PER_BATCH);
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
        String encodedBody =
                "data=" + URLEncoder.encode(buildOverpassQuery(batch), StandardCharsets.UTF_8);
        return parseResponse(http.postForm(properties.overpassUrl(), encodedBody));
    }

    static String buildOverpassQuery(List<AnchoredEnrichmentTarget> batch) {
        String clauses =
                batch.stream()
                        .map(OverpassPoiClient::buildTargetClause)
                        .collect(Collectors.joining());
        return "[out:json][timeout:15];(" + clauses + ");out center tags;";
    }

    static List<OpenPoiCandidate> parseResponse(JsonNode root) {
        JsonNode elements = root.path("elements");
        if (!elements.isArray() || elements.isEmpty()) {
            String remark = root.path("remark").asString("");
            String lowerRemark = remark.toLowerCase(Locale.ROOT);
            if (lowerRemark.contains("error") || lowerRemark.contains("timed out")) {
                throw new OpenDataResponseException("Overpass remark indicates error: " + remark);
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
        Optional<GeoPoint> coordinates = extractCoordinates(element);
        if (coordinates.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> tags = extractTags(element.path("tags"));
        String name =
                OpenDataHttp.firstNonBlank(
                        tags.get("name"),
                        tags.get("name:en"),
                        tags.get("int_name"),
                        tags.get("alt_name"),
                        tags.get("short_name"));
        if (name == null) {
            return Optional.empty();
        }
        String cityName =
                OpenDataHttp.firstNonBlank(
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
                        coordinates.get(),
                        tags,
                        cityName,
                        countryCode,
                        openingHours));
    }

    private static Optional<GeoPoint> extractCoordinates(JsonNode element) {
        JsonNode center = element.path("center");
        return OpenDataHttp.validPoint(element.get("lat"), element.get("lon"))
                .or(() -> OpenDataHttp.validPoint(center.get("lat"), center.get("lon")));
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
        return String.format(
                Locale.ROOT,
                "nwr(around:%.0f,%s,%s)[\"name\"][~\"%s\"~\".\"];",
                OpenPoiMatcher.searchRadiusMeters(target.coordSource()),
                OpenDataHttp.formatCoordinate(target.coordinates().latitude()),
                OpenDataHttp.formatCoordinate(target.coordinates().longitude()),
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

    private record SpatialCell(int latBucket, int lngBucket) implements Comparable<SpatialCell> {

        @Override
        public int compareTo(SpatialCell other) {
            int byLat = Integer.compare(latBucket, other.latBucket);
            return byLat != 0 ? byLat : Integer.compare(lngBucket, other.lngBucket);
        }
    }
}
