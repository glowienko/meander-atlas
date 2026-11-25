package com.meanderatlas.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.meanderatlas.TestEnvironment;
import com.meanderatlas.TestcontainersConfiguration;
import com.meanderatlas.atlas.AtlasPlaceView;
import com.meanderatlas.atlas.AtlasPlacesResponse;
import com.meanderatlas.atlas.BoundarySeedResultView;
import com.meanderatlas.atlas.PlaceVisitStateView;
import com.meanderatlas.atlas.UpdatePlaceVisitStatusRequest;
import com.meanderatlas.ingestion.domain.FixtureArchiveSupport;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

@Tag("e2e")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LocalImportE2eTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @TempDir static Path tempRoot;

    @LocalServerPort private int port;

    @Autowired private NamedParameterJdbcTemplate jdbc;

    @DynamicPropertySource
    static void registerImportProperties(DynamicPropertyRegistry registry) {
        registry.add("app.boundaries-dir", () -> tempRoot.resolve("boundaries").toString());
        registry.add("app.boundaries-auto-seed", () -> "false");
        registry.add("app.imports-dir", () -> tempRoot.resolve("imports").toString());
    }

    @BeforeEach
    @AfterEach
    void cleanupState() throws IOException {
        jdbc.update(
                "DELETE FROM import_batches WHERE user_id = :userId", Map.of("userId", USER_ID));
        jdbc.update("DELETE FROM places WHERE user_id = :userId", Map.of("userId", USER_ID));
        jdbc.update("DELETE FROM region_boundaries", Map.of());
        clearDirectory(tempRoot.resolve("boundaries"));
        clearDirectory(tempRoot.resolve("imports"));
    }

    @Test
    void importsSyntheticTakeoutCrossMatchesCidQueuesUnresolvedCsvAndRespectsPrecedence()
            throws IOException {
        writeSyntheticPolandBoundary();
        Path personalDir = tempRoot.resolve("imports/personal");
        FixtureArchiveSupport.extractSyntheticTakeout(personalDir);
        Files.copy(
                FixtureArchiveSupport.syntheticTimelinePath(),
                personalDir.resolve("Timeline.json"));

        RestClient client =
                RestClient.builder().baseUrl(TestEnvironment.localhostUrl(port)).build();

        ResponseEntity<BoundarySeedResultView> seeded =
                client.post()
                        .uri("/api/v1/atlas/boundaries/seed")
                        .retrieve()
                        .toEntity(BoundarySeedResultView.class);
        assertThat(seeded.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<LocalImportSummaryView> firstImport =
                client.post()
                        .uri("/api/v1/imports/local")
                        .retrieve()
                        .toEntity(LocalImportSummaryView.class);
        assertThat(firstImport.getStatusCode()).isEqualTo(HttpStatus.OK);
        LocalImportSummaryView summary = firstImport.getBody();
        assertThat(summary).isNotNull();
        assertThat(summary.accounts()).hasSize(1);
        AccountImportSummaryView personal = summary.accounts().getFirst();
        assertThat(personal.accountLabel()).isEqualTo("personal");
        assertThat(personal.skipped()).isFalse();
        assertThat(personal.discoveredFiles()).isEqualTo(6);
        assertThat(personal.importRecordsCreated()).isEqualTo(42);
        assertThat(personal.placesCreated()).isEqualTo(5);
        assertThat(personal.placesMerged()).isEqualTo(2);
        assertThat(personal.csvCidMatched()).isEqualTo(5);
        assertThat(personal.queuedResolutionJobs()).isEqualTo(20);
        assertThat(personal.timelineMatchedVisited()).isEqualTo(5);
        assertThat(personal.timelineMarkedTriage()).isZero();
        assertThat(personal.timelineUnmatched()).isEqualTo(5);

        ResponseEntity<ImportStatusView> statusResponse =
                client.get()
                        .uri("/api/v1/imports/status")
                        .retrieve()
                        .toEntity(ImportStatusView.class);
        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        ImportStatusView status = statusResponse.getBody();
        assertThat(status).isNotNull();
        assertThat(status.pendingResolutionCount()).isEqualTo(20);
        assertThat(status.completedBatches()).isEqualTo(1);
        assertThat(status.accounts()).containsExactly("personal");
        assertThat(status.lastImportedAt()).isNotNull();

        Integer unlinkedJobs =
                jdbc.queryForObject(
                        "SELECT COUNT(*)::int FROM resolution_jobs WHERE place_id IS NULL",
                        Map.of(),
                        Integer.class);
        assertThat(unlinkedJobs).isEqualTo(20);

        AtlasPlacesResponse places =
                client.get().uri("/api/v1/atlas/places").retrieve().body(AtlasPlacesResponse.class);
        assertThat(places).isNotNull();
        assertThat(places.places()).hasSize(5);

        AtlasPlaceView wawel =
                places.places().stream()
                        .filter(p -> p.canonicalName().equals("Wawel Royal Castle"))
                        .findFirst()
                        .orElseThrow();
        assertThat(wawel.visitStatus()).isEqualTo(VisitStatus.VISITED);
        assertThat(wawel.visitSource()).isEqualTo(VisitSource.REVIEW);
        assertThat(wawel.countryCode()).isEqualTo("PL");
        assertThat(wawel.memberships()).hasSize(3);

        AtlasPlaceView eiffel =
                places.places().stream()
                        .filter(p -> p.canonicalName().equals("Eiffel Tower"))
                        .findFirst()
                        .orElseThrow();
        assertThat(eiffel.visitStatus()).isEqualTo(VisitStatus.VISITED);
        assertThat(eiffel.visitSource()).isEqualTo(VisitSource.TIMELINE);

        ResponseEntity<LocalImportSummaryView> skippedSecondRun =
                client.post()
                        .uri("/api/v1/imports/local")
                        .retrieve()
                        .toEntity(LocalImportSummaryView.class);
        assertThat(skippedSecondRun.getBody()).isNotNull();
        assertThat(skippedSecondRun.getBody().accounts().getFirst().skipped()).isTrue();

        PlaceVisitStateView manualOverride =
                client.patch()
                        .uri("/api/v1/atlas/places/{id}/visit-status", eiffel.id())
                        .body(new UpdatePlaceVisitStatusRequest(VisitStatus.UNVISITED))
                        .retrieve()
                        .body(PlaceVisitStateView.class);
        assertThat(manualOverride).isNotNull();
        assertThat(manualOverride.status()).isEqualTo(VisitStatus.UNVISITED);
        assertThat(manualOverride.source()).isEqualTo(VisitSource.MANUAL_SINGLE);

        ResponseEntity<LocalImportSummaryView> forcedReimport =
                client.post()
                        .uri("/api/v1/imports/local?force=true")
                        .retrieve()
                        .toEntity(LocalImportSummaryView.class);
        assertThat(forcedReimport.getBody()).isNotNull();
        assertThat(forcedReimport.getBody().accounts().getFirst().skipped()).isFalse();

        AtlasPlacesResponse afterForce =
                client.get().uri("/api/v1/atlas/places").retrieve().body(AtlasPlacesResponse.class);
        assertThat(afterForce).isNotNull();
        AtlasPlaceView eiffelAfterForce =
                afterForce.places().stream()
                        .filter(p -> p.id().equals(eiffel.id()))
                        .findFirst()
                        .orElseThrow();
        assertThat(eiffelAfterForce.visitStatus()).isEqualTo(VisitStatus.UNVISITED);
        assertThat(eiffelAfterForce.visitSource()).isEqualTo(VisitSource.MANUAL_SINGLE);
    }

    @Test
    void marksUnvisitedNearbyPlaceAsNeedsTriageOnImpreciseTimelineVisit() throws IOException {
        Path triageAccountDir = tempRoot.resolve("imports/triage");
        Files.createDirectories(triageAccountDir);
        Files.writeString(
                triageAccountDir.resolve("Saved Places.json"),
                """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "geometry": {"type": "Point", "coordinates": [19.935412, 50.054049]},
                      "properties": {
                        "date": "2025-05-01T10:00:00Z",
                        "google_maps_url": "http://maps.google.com/?cid=536870919",
                        "location": {"country_code": "PL", "name": "Nearby Triage Landmark"}
                      }
                    }
                  ]
                }
                """,
                StandardCharsets.UTF_8);
        Files.writeString(
                triageAccountDir.resolve("Timeline.json"),
                """
                {
                  "semanticSegments": [
                    {
                      "startTime": "2026-08-10T09:00:00.000+02:00",
                      "endTime": "2026-08-10T10:00:00.000+02:00",
                      "visit": {
                        "hierarchyLevel": 0,
                        "probability": 0.90,
                        "topCandidate": {
                          "probability": 0.90,
                          "placeLocation": {"latLng": "50.0547700°, 19.9354120°"}
                        }
                      }
                    }
                  ]
                }
                """,
                StandardCharsets.UTF_8);

        RestClient client =
                RestClient.builder().baseUrl(TestEnvironment.localhostUrl(port)).build();
        ResponseEntity<LocalImportSummaryView> response =
                client.post()
                        .uri("/api/v1/imports/local?account=triage")
                        .retrieve()
                        .toEntity(LocalImportSummaryView.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LocalImportSummaryView summary = response.getBody();
        assertThat(summary).isNotNull();
        assertThat(summary.totalPlacesCreated()).isEqualTo(1);
        assertThat(summary.totalTimelineMarkedTriage()).isEqualTo(1);

        AtlasPlacesResponse places =
                client.get().uri("/api/v1/atlas/places").retrieve().body(AtlasPlacesResponse.class);
        assertThat(places).isNotNull();
        assertThat(places.places()).hasSize(1);
        assertThat(places.places().getFirst().visitStatus()).isEqualTo(VisitStatus.NEEDS_TRIAGE);
    }

    @Test
    void upgradesUncategorizedPlaceCategoryViaCsvCidMatchAndHandlesMissingAccount()
            throws IOException {
        Path upgradeDir = tempRoot.resolve("imports/upgrade");
        Files.createDirectories(upgradeDir);
        Files.writeString(
                upgradeDir.resolve("Saved Places.json"),
                """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "geometry": {"type": "Point", "coordinates": [19.94, 50.06]},
                      "properties": {
                        "date": "2025-05-01T10:00:00Z",
                        "google_maps_url": "http://maps.google.com/?cid=536870945",
                        "location": {"country_code": "   ", "name": "Old Brick Building"}
                      }
                    }
                  ]
                }
                """,
                StandardCharsets.UTF_8);
        Files.writeString(
                upgradeDir.resolve("Want to go.csv"),
                """
                Title,Note,URL,Tags,Comment
                Old Brick Building,Great history museum,https://www.google.com/maps/place/Old+Brick+Building/data=!4m2!3m1!1s0x10000021:0x20000021,history,
                """,
                StandardCharsets.UTF_8);

        RestClient client =
                RestClient.builder().baseUrl(TestEnvironment.localhostUrl(port)).build();
        LocalImportSummaryView emptySummary =
                client.post()
                        .uri("/api/v1/imports/local?account=nonexistent")
                        .retrieve()
                        .body(LocalImportSummaryView.class);
        assertThat(emptySummary).isNotNull();
        assertThat(emptySummary.accounts()).isEmpty();

        LocalImportSummaryView upgradeSummary =
                client.post()
                        .uri("/api/v1/imports/local?account=upgrade")
                        .retrieve()
                        .body(LocalImportSummaryView.class);
        assertThat(upgradeSummary).isNotNull();
        assertThat(upgradeSummary.totalPlacesCreated()).isEqualTo(1);
        assertThat(upgradeSummary.totalCsvCidMatched()).isEqualTo(1);

        AtlasPlacesResponse places =
                client.get().uri("/api/v1/atlas/places").retrieve().body(AtlasPlacesResponse.class);
        assertThat(places).isNotNull();
        assertThat(places.places()).hasSize(1);
        assertThat(places.places().getFirst().category()).isEqualTo(PlaceCategory.MUSEUM);
        assertThat(places.places().getFirst().subcategory()).isEqualTo("museum");
    }

    private static void writeSyntheticPolandBoundary() throws IOException {
        Path boundariesDir = tempRoot.resolve("boundaries");
        Files.createDirectories(boundariesDir);
        Files.writeString(
                boundariesDir.resolve("ne_10m_admin_0_countries.geojson"),
                """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "properties": {"ISO_A2_EH": "PL", "ADMIN": "Poland"},
                      "geometry": {
                        "type": "MultiPolygon",
                        "coordinates": [[[[18.0, 49.0], [22.0, 49.0], [22.0, 54.4], [18.0, 54.4], [18.0, 49.0]]]]
                      }
                    }
                  ]
                }
                """,
                StandardCharsets.UTF_8);
        Files.writeString(
                boundariesDir.resolve("ne_10m_admin_1_states_provinces.geojson"),
                """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "properties": {"iso_3166_2": "PL-12", "iso_a2": "PL", "name": "Małopolskie"},
                      "geometry": {
                        "type": "Polygon",
                        "coordinates": [[[19.0, 49.4], [21.2, 49.4], [21.2, 50.6], [19.0, 50.6], [19.0, 49.4]]]
                      }
                    }
                  ]
                }
                """,
                StandardCharsets.UTF_8);
    }

    private static void clearDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.compareTo(a))
                    .filter(p -> !p.equals(dir))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        }
    }
}
