package com.meanderatlas.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.meanderatlas.TestEnvironment;
import com.meanderatlas.TestcontainersConfiguration;
import com.meanderatlas.atlas.AtlasPlaceView;
import com.meanderatlas.atlas.AtlasPlacesResponse;
import com.meanderatlas.atlas.BoundarySeedResultView;
import com.meanderatlas.atlas.domain.boundaries.BoundaryTestData;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.PlaceCategory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Tag("e2e")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EnrichmentE2eTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @TempDir static Path tempRoot;

    private static HttpServer mockApiServer;
    private static int mockPort;

    @LocalServerPort private int port;

    @Autowired private NamedParameterJdbcTemplate jdbc;

    @BeforeAll
    static void startMockHttpServer() throws IOException {
        mockApiServer =
                HttpServer.create(
                        new InetSocketAddress(
                                InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 0),
                        0);
        mockPort = mockApiServer.getAddress().getPort();
        mockApiServer.createContext("/overpass", EnrichmentE2eTest::handleOverpass);
        mockApiServer.createContext("/photon", EnrichmentE2eTest::handlePhoton);
        mockApiServer.createContext("/commons", EnrichmentE2eTest::handleCommons);
        mockApiServer.createContext("/wiki", EnrichmentE2eTest::handleWikipedia);
        mockApiServer.createContext("/wikidata", EnrichmentE2eTest::handleWikidata);
        mockApiServer.start();
    }

    @AfterAll
    static void stopMockHttpServer() {
        if (mockApiServer != null) {
            mockApiServer.stop(0);
        }
    }

    @DynamicPropertySource
    static void registerEnrichmentProperties(DynamicPropertyRegistry registry) {
        String baseUrl = "http://127.0.0.1:" + mockPort;
        registry.add("app.boundaries-dir", () -> tempRoot.resolve("boundaries").toString());
        registry.add("app.boundaries-auto-seed", () -> "false");
        registry.add("app.imports-dir", () -> tempRoot.resolve("imports").toString());
        registry.add("app.enrichment.enabled", () -> "true");
        registry.add("app.enrichment.batch-size", () -> "10");
        registry.add("app.enrichment.request-delay-ms", () -> "0");
        registry.add("app.enrichment.max-retries", () -> "2");
        registry.add("app.enrichment.overpass-url", () -> baseUrl + "/overpass");
        registry.add("app.enrichment.photon-url", () -> baseUrl + "/photon");
        registry.add("app.enrichment.wikimedia-commons-api-url", () -> baseUrl + "/commons");
        registry.add("app.enrichment.wikipedia-rest-url-template", () -> baseUrl + "/wiki/{lang}");
        registry.add("app.enrichment.wikidata-api-url", () -> baseUrl + "/wikidata");
    }

    @BeforeEach
    @AfterEach
    void cleanupDatabase() {
        jdbc.update(
                "DELETE FROM resolution_jobs WHERE user_id = :userId", Map.of("userId", USER_ID));
        jdbc.update(
                "DELETE FROM import_batches WHERE user_id = :userId", Map.of("userId", USER_ID));
        jdbc.update("DELETE FROM places WHERE user_id = :userId", Map.of("userId", USER_ID));
        jdbc.update("DELETE FROM region_boundaries", Map.of());
    }

    @Test
    void rejectsInvalidLimitWithProblemDetailAndExecutesDryRunThenLiveEnrichment()
            throws IOException {
        RestClient client =
                RestClient.builder().baseUrl(TestEnvironment.localhostUrl(port)).build();

        assertThatThrownBy(() ->
                        client.post()
                                .uri("/api/v1/imports/enrich?limit=0")
                                .retrieve()
                                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class);

        BoundaryTestData.writePolandOnly(tempRoot.resolve("boundaries"));
        ResponseEntity<BoundarySeedResultView> seeded =
                client.post()
                        .uri("/api/v1/atlas/boundaries/seed")
                        .retrieve()
                        .toEntity(BoundarySeedResultView.class);
        assertThat(seeded.getStatusCode()).isEqualTo(HttpStatus.OK);

        UUID approxPlaceId =
                insertPlace(
                        "Wawel Royal Castle",
                        50.0480,
                        19.9280,
                        "TAKEOUT_S2_APPROX",
                        "UNCATEGORIZED",
                        "HEURISTIC");
        UUID exactManualPlaceId =
                insertPlace("Cafe Camelot", 50.0630, 19.9390, "TAKEOUT", "MUSEUM", "MANUAL");
        insertEnrichJob(approxPlaceId);
        insertEnrichJob(exactManualPlaceId);

        UUID batchId = insertImportBatch();
        insertCoordinateLookupJob(
                batchId,
                "Sukiennice",
                "https://www.google.com/maps/search/?api=1&query=Sukiennice",
                "{\"title\":\"Sukiennice\",\"listName\":\"Want to go\"}");
        insertCoordinateLookupJob(
                batchId,
                "Dropped Pin",
                "https://www.google.com/maps/search/?api=1&query=0,0",
                "{\"title\":\"Dropped Pin\",\"listName\":\"Want to go\"}");

        ImportStatusView beforeStatus =
                client.get().uri("/api/v1/imports/status").retrieve().body(ImportStatusView.class);
        assertThat(beforeStatus).isNotNull();
        assertThat(beforeStatus.pendingCoordinateLookupCount()).isEqualTo(2);
        assertThat(beforeStatus.pendingEnrichmentCount()).isEqualTo(2);
        assertThat(beforeStatus.approximatePlaceCount()).isEqualTo(1);
        assertThat(beforeStatus.enrichedPlaceCount()).isZero();
        assertThat(beforeStatus.enrichmentRunning()).isFalse();

        EnrichmentBatchResultView dryRun =
                client.post()
                        .uri("/api/v1/imports/enrich?limit=20&dryRun=true")
                        .retrieve()
                        .body(EnrichmentBatchResultView.class);
        assertThat(dryRun).isNotNull();
        assertThat(dryRun.enabled()).isTrue();
        assertThat(dryRun.dryRun()).isTrue();
        assertThat(dryRun.claimedCount()).isEqualTo(4);
        assertThat(dryRun.matchedCount()).isEqualTo(3);
        assertThat(dryRun.upgradedCoordsCount()).isEqualTo(2);
        assertThat(dryRun.upgradedCategoryCount()).isEqualTo(2);
        assertThat(dryRun.photosAddedCount()).isEqualTo(1);
        assertThat(dryRun.skippedCount()).isEqualTo(1);
        assertThat(dryRun.remainingPendingCount()).isEqualTo(4);

        EnrichmentBatchResultView live =
                client.post()
                        .uri("/api/v1/imports/enrich?limit=20&dryRun=false")
                        .retrieve()
                        .body(EnrichmentBatchResultView.class);
        assertThat(live).isNotNull();
        assertThat(live.enabled()).isTrue();
        assertThat(live.dryRun()).isFalse();
        assertThat(live.claimedCount()).isEqualTo(4);
        assertThat(live.matchedCount()).isEqualTo(3);
        assertThat(live.upgradedCoordsCount()).isEqualTo(2);
        assertThat(live.upgradedCategoryCount()).isEqualTo(2);
        assertThat(live.photosAddedCount()).isEqualTo(1);
        assertThat(live.skippedCount()).isEqualTo(1);
        assertThat(live.remainingPendingCount()).isZero();

        AtlasPlacesResponse placesResponse =
                client.get().uri("/api/v1/atlas/places").retrieve().body(AtlasPlacesResponse.class);
        assertThat(placesResponse).isNotNull();
        List<AtlasPlaceView> places = placesResponse.places();
        assertThat(places).hasSize(3);

        AtlasPlaceView wawel = findByName(places, "Wawel Royal Castle");
        assertThat(wawel.coordSource()).isEqualTo(CoordSource.OPEN_CATALOG);
        assertThat(wawel.lat()).isCloseTo(50.0541, within(1e-4));
        assertThat(wawel.lng()).isCloseTo(19.9355, within(1e-4));
        assertThat(wawel.category()).isEqualTo(PlaceCategory.LANDMARK);
        assertThat(wawel.categorySource()).isEqualTo(CategorySource.OPEN_CATALOG);
        assertThat(wawel.countryCode()).isEqualTo("PL");
        assertThat(wawel.admin1Code()).isEqualTo("PL-12");
        assertThat(wawel.cityName()).isEqualTo("Kraków");
        assertThat(wawel.photoUrl())
                .isEqualTo("https://upload.wikimedia.org/wikipedia/commons/thumb/w/Wawel.jpg");

        String openingHoursJson =
                jdbc.queryForObject(
                        "SELECT opening_hours_json::text FROM places WHERE id = :id",
                        Map.of("id", approxPlaceId),
                        String.class);
        assertThat(openingHoursJson).contains("Mo-Su 09:00-17:00");

        AtlasPlaceView camelot = findByName(places, "Cafe Camelot");
        assertThat(camelot.coordSource()).isEqualTo(CoordSource.TAKEOUT);
        assertThat(camelot.lat()).isCloseTo(50.0630, within(1e-4));
        assertThat(camelot.category()).isEqualTo(PlaceCategory.MUSEUM);
        assertThat(camelot.categorySource()).isEqualTo(CategorySource.MANUAL);
        assertThat(camelot.cityName()).isEqualTo("Kraków");

        AtlasPlaceView sukiennice = findByName(places, "Sukiennice");
        assertThat(sukiennice.coordSource()).isEqualTo(CoordSource.OPEN_CATALOG);
        assertThat(sukiennice.category()).isEqualTo(PlaceCategory.MUSEUM);
        assertThat(sukiennice.countryCode()).isEqualTo("PL");

        ImportStatusView afterStatus =
                client.get().uri("/api/v1/imports/status").retrieve().body(ImportStatusView.class);
        assertThat(afterStatus).isNotNull();
        assertThat(afterStatus.pendingCoordinateLookupCount()).isZero();
        assertThat(afterStatus.pendingEnrichmentCount()).isZero();
        assertThat(afterStatus.approximatePlaceCount()).isZero();
        assertThat(afterStatus.enrichedPlaceCount()).isEqualTo(3);
        assertThat(afterStatus.enrichmentRunning()).isFalse();
    }

    private UUID insertPlace(
            String name,
            double lat,
            double lng,
            String coordSource,
            String category,
            String categorySource) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO places (
                    id, user_id, canonical_name, geom, coord_source,
                    category, category_source, derived_role
                )
                VALUES (
                    :id, :userId, :name, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), :coordSource,
                    :category, :categorySource, 'BACKLOG'
                )
                """,
                Map.of(
                        "id",
                        id,
                        "userId",
                        USER_ID,
                        "name",
                        name,
                        "lng",
                        lng,
                        "lat",
                        lat,
                        "coordSource",
                        coordSource,
                        "category",
                        category,
                        "categorySource",
                        categorySource));
        return id;
    }

    private void insertEnrichJob(UUID placeId) {
        jdbc.update(
                """
                INSERT INTO resolution_jobs (id, user_id, place_id, job_type, status, attempts)
                VALUES (:id, :userId, :placeId, 'OPEN_CATALOG_ENRICH', 'PENDING', 0)
                """,
                Map.of("id", UUID.randomUUID(), "userId", USER_ID, "placeId", placeId));
    }

    private UUID insertImportBatch() {
        UUID batchId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO import_batches (
                    id, user_id, account_label, content_sha256, source_type, status, record_count
                )
                VALUES (:id, :userId, 'personal', :sha, 'LOCAL_DIRECTORY', 'COMPLETED', 2)
                """,
                Map.of("id", batchId, "userId", USER_ID, "sha", UUID.randomUUID().toString()));
        return batchId;
    }

    private void insertCoordinateLookupJob(
            UUID batchId, String title, String url, String payloadJson) {
        UUID recordId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO import_records (
                    id, batch_id, user_id, account_label, record_type, source_file,
                    raw_title, raw_url, payload_json
                )
                VALUES (
                    :id, :batchId, :userId, 'personal', 'SAVED_CSV', 'Want to go.csv',
                    :title, :url, CAST(:payloadJson AS jsonb)
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", recordId)
                        .addValue("batchId", batchId)
                        .addValue("userId", USER_ID)
                        .addValue("title", title, Types.VARCHAR)
                        .addValue("url", url, Types.VARCHAR)
                        .addValue("payloadJson", payloadJson));
        jdbc.update(
                """
                INSERT INTO resolution_jobs (
                    id, user_id, import_record_id, job_type, status, attempts
                )
                VALUES (:id, :userId, :recordId, 'COORDINATE_LOOKUP', 'PENDING', 0)
                """,
                Map.of("id", UUID.randomUUID(), "userId", USER_ID, "recordId", recordId));
    }

    private static AtlasPlaceView findByName(List<AtlasPlaceView> places, String name) {
        return places.stream()
                .filter(p -> name.equals(p.canonicalName()))
                .findFirst()
                .orElseThrow();
    }

    private static void handleOverpass(HttpExchange exchange) throws IOException {
        respondJson(
                exchange,
                """
                {
                  "elements": [
                    {
                      "type": "way",
                      "id": 1001,
                      "center": { "lat": 50.0541, "lon": 19.9355 },
                      "tags": {
                        "name": "Zamek Królewski na Wawelu",
                        "name:en": "Wawel Royal Castle",
                        "historic": "castle",
                        "addr:city": "Kraków",
                        "opening_hours": "Mo-Su 09:00-17:00",
                        "wikimedia_commons": "File:Wawel.jpg"
                      }
                    }
                  ]
                }
                """);
    }

    private static void handlePhoton(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        if (query != null && query.contains("Camelot")) {
            respondJson(
                    exchange,
                    """
                    {
                      "features": [
                        {
                          "geometry": { "coordinates": [19.9392, 50.0631] },
                          "properties": {
                            "osm_type": "N",
                            "osm_id": 2002,
                            "osm_key": "amenity",
                            "osm_value": "cafe",
                            "name": "Cafe Camelot",
                            "city": "Kraków",
                            "countrycode": "pl"
                          }
                        }
                      ]
                    }
                    """);
            return;
        }
        if (query != null && query.contains("Sukiennice")) {
            respondJson(
                    exchange,
                    """
                    {
                      "features": [
                        {
                          "geometry": { "coordinates": [19.9373, 50.0617] },
                          "properties": {
                            "osm_type": "W",
                            "osm_id": 3003,
                            "osm_key": "tourism",
                            "osm_value": "museum",
                            "name": "Sukiennice",
                            "city": "Kraków",
                            "countrycode": "pl"
                          }
                        }
                      ]
                    }
                    """);
            return;
        }
        respondJson(exchange, "{\"features\":[]}");
    }

    private static void handleCommons(HttpExchange exchange) throws IOException {
        respondJson(
                exchange,
                """
                {
                  "query": {
                    "pages": [
                      {
                        "imageinfo": [
                          { "thumburl": "https://upload.wikimedia.org/wikipedia/commons/thumb/w/Wawel.jpg" }
                        ]
                      }
                    ]
                  }
                }
                """);
    }

    private static void handleWikipedia(HttpExchange exchange) throws IOException {
        respondJson(exchange, "{}");
    }

    private static void handleWikidata(HttpExchange exchange) throws IOException {
        respondJson(exchange, "{}");
    }

    private static void respondJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
