package com.meanderatlas.ingestion;

import com.meanderatlas.ingestion.application.EnrichmentProperties;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.AnchoredEnrichmentTarget;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.PlaceId;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

public final class OpenCatalogTestData {

    public static final String USER_AGENT = "MeanderAtlas/0.1-test";
    public static final String OVERPASS_URL = "https://overpass.example/api/interpreter";
    public static final String PHOTON_URL = "https://photon.example/api";
    public static final String COMMONS_URL = "https://commons.example/w/api.php";
    public static final String WAWEL_THUMB_URL =
            "https://upload.wikimedia.org/wikipedia/commons/thumb/w/Wawel.jpg";
    public static final String NO_FEATURES = "{\"features\":[]}";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private OpenCatalogTestData() {}

    public static EnrichmentProperties properties() {
        return properties(true, 25, 2);
    }

    public static EnrichmentProperties properties(boolean enabled, int batchSize, int maxRetries) {
        return new EnrichmentProperties(
                enabled,
                batchSize,
                maxRetries,
                1000,
                5000,
                OVERPASS_URL,
                PHOTON_URL,
                COMMONS_URL,
                "https://{lang}.wikipedia.example/api/rest_v1",
                "https://wikidata.example/w/api.php",
                USER_AGENT);
    }

    public static AnchoredEnrichmentTarget anchoredTarget(
            String name,
            double lat,
            double lng,
            CoordSource coordSource,
            @Nullable String countryCode) {
        return new AnchoredEnrichmentTarget(
                PlaceId.random(),
                name,
                new GeoPoint(lat, lng),
                coordSource,
                CategorySource.HEURISTIC,
                null,
                null,
                countryCode,
                null);
    }

    public static Map<String, Object> overpassWay(
            long id, double lat, double lon, Map<String, String> tags) {
        return Map.of(
                "type", "way", "id", id, "center", Map.of("lat", lat, "lon", lon), "tags", tags);
    }

    public static Map<String, Object> overpassNode(
            long id, double lat, double lon, Map<String, String> tags) {
        return Map.of("type", "node", "id", id, "lat", lat, "lon", lon, "tags", tags);
    }

    public static String overpassResponse(Object... elements) {
        return json(Map.of("elements", List.of(elements)));
    }

    public static Map<String, Object> photonPoi(
            String osmRef,
            String osmTag,
            String name,
            String city,
            String countryCode,
            double lat,
            double lon) {
        String[] ref = osmRef.split("/", 2);
        String[] tag = osmTag.split("=", 2);
        return photonFeature(
                lat,
                lon,
                Map.of(
                        "osm_type",
                        ref[0],
                        "osm_id",
                        Long.parseLong(ref[1]),
                        "osm_key",
                        tag[0],
                        "osm_value",
                        tag[1],
                        "name",
                        name,
                        "city",
                        city,
                        "countrycode",
                        countryCode));
    }

    public static Map<String, Object> photonFeature(
            double lat, double lon, Map<String, Object> properties) {
        return Map.of(
                "geometry", Map.of("coordinates", List.of(lon, lat)), "properties", properties);
    }

    public static String photonResponse(Object... features) {
        return json(Map.of("features", List.of(features)));
    }

    public static String commonsThumbResponse(String thumbUrl) {
        return json(
                Map.of(
                        "query",
                        Map.of(
                                "pages",
                                List.of(
                                        Map.of(
                                                "imageinfo",
                                                List.of(Map.of("thumburl", thumbUrl)))))));
    }

    public static String json(Object value) {
        return JSON_MAPPER.writeValueAsString(value);
    }
}
