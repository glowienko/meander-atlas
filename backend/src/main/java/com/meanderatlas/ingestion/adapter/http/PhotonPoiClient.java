package com.meanderatlas.ingestion.adapter.http;

import com.meanderatlas.ingestion.domain.TextNormalizer;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.OpenPoiCandidate;
import com.meanderatlas.shared.GeoPoint;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PhotonPoiClient {

    private static final int DEFAULT_LIMIT = 5;
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final RestClient restClient;
    private final EnrichmentProperties properties;

    PhotonPoiClient(RestClient.Builder builder, EnrichmentProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    List<OpenPoiCandidate> search(String query, @Nullable GeoPoint anchor) {
        String normalizedQuery = TextNormalizer.trimToNull(query);
        if (normalizedQuery == null) {
            return List.of();
        }
        UriComponentsBuilder builder =
                UriComponentsBuilder.fromUriString(properties.photonUrl())
                        .queryParam("q", normalizedQuery);
        if (anchor != null) {
            builder.queryParam("lat", String.format(Locale.ROOT, "%.6f", anchor.latitude()))
                    .queryParam("lon", String.format(Locale.ROOT, "%.6f", anchor.longitude()));
        }
        URI uri = builder.queryParam("limit", DEFAULT_LIMIT).build().encode().toUri();
        String rawResponse =
                restClient
                        .get()
                        .uri(uri)
                        .header(HttpHeaders.USER_AGENT, properties.wikimediaUserAgent())
                        .retrieve()
                        .body(String.class);
        return parseResponse(rawResponse);
    }

    static List<OpenPoiCandidate> parseResponse(@Nullable String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return List.of();
        }
        JsonNode root;
        try {
            root = JSON_MAPPER.readTree(rawResponse);
        } catch (JacksonException ex) {
            return List.of();
        }
        JsonNode features = root.path("features");
        if (!features.isArray() || features.isEmpty()) {
            return List.of();
        }
        return StreamSupport.stream(features.spliterator(), false)
                .map(PhotonPoiClient::parseFeature)
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<OpenPoiCandidate> parseFeature(JsonNode feature) {
        GeoPoint coordinates = extractCoordinates(feature.path("geometry").path("coordinates"));
        if (coordinates == null) {
            return Optional.empty();
        }
        JsonNode props = feature.path("properties");
        if (!props.isObject()) {
            return Optional.empty();
        }
        String name =
                firstNonBlank(
                        textOrNull(props, "name"),
                        textOrNull(props, "street"),
                        textOrNull(props, "city"));
        if (name == null) {
            return Optional.empty();
        }
        String osmId = formatOsmId(textOrNull(props, "osm_type"), props.get("osm_id"));
        String osmKey = textOrNull(props, "osm_key");
        String osmValue = textOrNull(props, "osm_value");
        String cityName =
                firstNonBlank(
                        textOrNull(props, "city"),
                        textOrNull(props, "town"),
                        textOrNull(props, "village"));
        String countryCode =
                Optional.ofNullable(textOrNull(props, "countrycode"))
                        .map(code -> code.toUpperCase(Locale.ROOT))
                        .orElse(null);

        Stream.Builder<Map.Entry<String, String>> tagBuilder = Stream.builder();
        tagBuilder.add(Map.entry("name", name));
        if (osmKey != null && osmValue != null) {
            tagBuilder.add(Map.entry(osmKey, osmValue));
        }
        Map<String, String> tags =
                tagBuilder
                        .build()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        Map.Entry::getKey, Map.Entry::getValue, (a, _) -> a));
        return Optional.of(
                new OpenPoiCandidate(osmId, name, coordinates, tags, cityName, countryCode, null));
    }

    private static @Nullable GeoPoint extractCoordinates(JsonNode coords) {
        if (!coords.isArray() || coords.size() < 2) {
            return null;
        }
        JsonNode lngNode = coords.get(0);
        JsonNode latNode = coords.get(1);
        if (!lngNode.isNumber() || !latNode.isNumber()) {
            return null;
        }
        double lat = latNode.doubleValue();
        double lng = lngNode.doubleValue();
        if (!Double.isFinite(lat)
                || !Double.isFinite(lng)
                || lat < -90.0
                || lat > 90.0
                || lng < -180.0
                || lng > 180.0) {
            return null;
        }
        return new GeoPoint(lat, lng);
    }

    private static @Nullable String formatOsmId(
            @Nullable String osmType, @Nullable JsonNode osmIdNode) {
        if (osmType == null || osmIdNode == null || osmIdNode.isNull()) {
            return null;
        }
        long id = osmIdNode.asLong(-1L);
        if (id <= 0) {
            return null;
        }
        String normalizedType =
                switch (osmType.toUpperCase(Locale.ROOT)) {
                    case "N" -> "node";
                    case "W" -> "way";
                    case "R" -> "relation";
                    default -> null;
                };
        return normalizedType == null ? null : normalizedType + "/" + id;
    }

    private static @Nullable String textOrNull(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull() || !field.isString()) {
            return null;
        }
        return TextNormalizer.trimToNull(field.stringValue());
    }

    private static @Nullable String firstNonBlank(@Nullable String... candidates) {
        return Stream.of(candidates)
                .map(TextNormalizer::trimToNull)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
