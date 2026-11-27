package com.meanderatlas.ingestion.adapter.http;

import com.meanderatlas.ingestion.application.EnrichmentProperties;
import com.meanderatlas.ingestion.domain.TextNormalizer;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.OpenPoiCandidate;
import com.meanderatlas.shared.GeoPoint;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

class PhotonPoiClient {

    private static final int DEFAULT_LIMIT = 5;
    private static final int REVERSE_LIMIT = 3;

    private final OpenDataHttp http;
    private final EnrichmentProperties properties;

    PhotonPoiClient(RestClient.Builder builder, EnrichmentProperties properties) {
        this.http = new OpenDataHttp(builder, properties.wikimediaUserAgent());
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
            builder.queryParam("lat", OpenDataHttp.formatCoordinate(anchor.latitude()))
                    .queryParam("lon", OpenDataHttp.formatCoordinate(anchor.longitude()));
        }
        URI uri = builder.queryParam("limit", DEFAULT_LIMIT).build().encode().toUri();
        return parseResponse(http.get(uri));
    }

    List<OpenPoiCandidate> reverse(GeoPoint point) {
        URI uri =
                UriComponentsBuilder.fromUriString(resolveReverseUrl(properties.photonUrl()))
                        .queryParam("lat", OpenDataHttp.formatCoordinate(point.latitude()))
                        .queryParam("lon", OpenDataHttp.formatCoordinate(point.longitude()))
                        .queryParam("limit", REVERSE_LIMIT)
                        .build()
                        .encode()
                        .toUri();
        return parseResponse(http.get(uri));
    }

    static String resolveReverseUrl(String photonUrl) {
        String trimmed =
                photonUrl.endsWith("/")
                        ? photonUrl.substring(0, photonUrl.length() - 1)
                        : photonUrl;
        if (trimmed.endsWith("/api")) {
            return trimmed.substring(0, trimmed.length() - 4) + "/reverse";
        }
        return trimmed + "/reverse";
    }

    static List<OpenPoiCandidate> parseResponse(JsonNode root) {
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
        JsonNode coords = feature.path("geometry").path("coordinates");
        Optional<GeoPoint> coordinates = OpenDataHttp.validPoint(coords.get(1), coords.get(0));
        JsonNode props = feature.path("properties");
        if (coordinates.isEmpty() || !props.isObject()) {
            return Optional.empty();
        }
        String name =
                OpenDataHttp.firstNonBlank(
                        textOrNull(props, "name"),
                        textOrNull(props, "street"),
                        textOrNull(props, "district"),
                        textOrNull(props, "locality"),
                        textOrNull(props, "city"),
                        textOrNull(props, "county"));
        if (name == null) {
            return Optional.empty();
        }
        String osmId = formatOsmId(textOrNull(props, "osm_type"), props.get("osm_id"));
        String osmKey = textOrNull(props, "osm_key");
        String osmValue = textOrNull(props, "osm_value");
        String cityName =
                OpenDataHttp.firstNonBlank(
                        textOrNull(props, "city"),
                        textOrNull(props, "town"),
                        textOrNull(props, "village"));
        String countryCode =
                Optional.ofNullable(textOrNull(props, "countrycode"))
                        .map(code -> code.toUpperCase(Locale.ROOT))
                        .orElse(null);

        Map<String, String> tags =
                osmKey == null || osmValue == null || osmKey.equals("name")
                        ? Map.of("name", name)
                        : Map.of("name", name, osmKey, osmValue);
        return Optional.of(
                new OpenPoiCandidate(
                        osmId, name, coordinates.get(), tags, cityName, countryCode, null));
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
}
