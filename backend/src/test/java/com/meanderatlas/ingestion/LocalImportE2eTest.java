package com.meanderatlas.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.meanderatlas.TestEnvironment;
import com.meanderatlas.TestcontainersConfiguration;
import com.meanderatlas.atlas.AtlasPlaceView;
import com.meanderatlas.atlas.AtlasPlacesResponse;
import com.meanderatlas.atlas.BoundarySeedResultView;
import com.meanderatlas.atlas.PlaceVisitStateView;
import com.meanderatlas.atlas.UpdatePlaceVisitStatusRequest;
import com.meanderatlas.atlas.domain.boundaries.BoundaryTestData;
import com.meanderatlas.ingestion.domain.FixtureArchiveSupport;
import com.meanderatlas.ingestion.domain.TakeoutTestData;
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
        stagePersonalSyntheticTakeout();
        RestClient client = restClient();

        seedSyntheticPolandBoundary(client);
        verifyPersonalInitialImport(client);
        verifyImportStatusEndpoint(client);
        verifyManualPrecedenceOnForceReimport(client);
    }

    @Test
    void marksUnvisitedNearbyPlaceAsNeedsTriageOnImpreciseTimelineVisit() throws IOException {
        Path triageDir = tempRoot.resolve("imports/triage");
        Files.createDirectories(triageDir);
        Files.writeString(
                triageDir.resolve("Saved Places.json"),
                TakeoutTestData.savedPlaceGeoJson(
                        "Nearby Triage Landmark", 19.935412, 50.054049, 536870919L, "PL"),
                StandardCharsets.UTF_8);
        Files.writeString(
                triageDir.resolve("Timeline.json"),
                TakeoutTestData.singleVisitTimelineJson(
                        "2026-08-10T09:00:00.000+02:00",
                        "2026-08-10T10:00:00.000+02:00",
                        50.0547700,
                        19.9354120),
                StandardCharsets.UTF_8);

        LocalImportSummaryView summary =
                restClient()
                        .post()
                        .uri("/api/v1/imports/local?account=triage")
                        .retrieve()
                        .body(LocalImportSummaryView.class);

        assertThat(summary).isNotNull();
        assertThat(summary.totalPlacesCreated()).isEqualTo(1);
        assertThat(summary.totalTimelineMarkedTriage()).isEqualTo(1);
        assertThat(fetchAllPlaces(restClient()))
                .extracting(AtlasPlaceView::visitStatus)
                .containsExactly(VisitStatus.NEEDS_TRIAGE);
    }

    @Test
    void upgradesUncategorizedPlaceCategoryViaCsvCidMatchAndHandlesMissingAccount()
            throws IOException {
        Path upgradeDir = tempRoot.resolve("imports/upgrade");
        Files.createDirectories(upgradeDir);
        Files.writeString(
                upgradeDir.resolve("Saved Places.json"),
                TakeoutTestData.savedPlaceGeoJson(
                        "Old Brick Building", 19.94, 50.06, 536870945L, "   "),
                StandardCharsets.UTF_8);
        Files.writeString(
                upgradeDir.resolve("Want to go.csv"),
                TakeoutTestData.savedCsv(
                        "Old Brick Building,Great history museum,"
                                + "https://www.google.com/maps/place/Old+Brick+Building/data=!4m2!3m1!1s0x10000021:0x20000021,history,"),
                StandardCharsets.UTF_8);

        RestClient client = restClient();
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

        ListPlaceAssertions.assertSingleCategory(
                fetchAllPlaces(client), PlaceCategory.MUSEUM, "museum");
    }

    private RestClient restClient() {
        return RestClient.builder().baseUrl(TestEnvironment.localhostUrl(port)).build();
    }

    private static void stagePersonalSyntheticTakeout() throws IOException {
        BoundaryTestData.writePolandOnly(tempRoot.resolve("boundaries"));
        Path personalDir = tempRoot.resolve("imports/personal");
        FixtureArchiveSupport.extractSyntheticTakeout(personalDir);
        Files.copy(
                FixtureArchiveSupport.syntheticTimelinePath(),
                personalDir.resolve("Timeline.json"));
    }

    private static void seedSyntheticPolandBoundary(RestClient client) {
        ResponseEntity<BoundarySeedResultView> seeded =
                client.post()
                        .uri("/api/v1/atlas/boundaries/seed")
                        .retrieve()
                        .toEntity(BoundarySeedResultView.class);
        assertThat(seeded.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private static void verifyPersonalInitialImport(RestClient client) {
        LocalImportSummaryView summary =
                client.post()
                        .uri("/api/v1/imports/local")
                        .retrieve()
                        .body(LocalImportSummaryView.class);
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
    }

    private void verifyImportStatusEndpoint(RestClient client) {
        ImportStatusView status =
                client.get().uri("/api/v1/imports/status").retrieve().body(ImportStatusView.class);
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
    }

    private static void verifyManualPrecedenceOnForceReimport(RestClient client) {
        java.util.List<AtlasPlaceView> places = fetchAllPlaces(client);
        assertThat(places).hasSize(5);

        AtlasPlaceView wawel = findPlaceByName(places, "Wawel Royal Castle");
        assertThat(wawel.visitStatus()).isEqualTo(VisitStatus.VISITED);
        assertThat(wawel.visitSource()).isEqualTo(VisitSource.REVIEW);
        assertThat(wawel.countryCode()).isEqualTo("PL");
        assertThat(wawel.memberships()).hasSize(3);

        AtlasPlaceView eiffel = findPlaceByName(places, "Eiffel Tower");
        assertThat(eiffel.visitStatus()).isEqualTo(VisitStatus.VISITED);
        assertThat(eiffel.visitSource()).isEqualTo(VisitSource.TIMELINE);

        LocalImportSummaryView skippedRun =
                client.post()
                        .uri("/api/v1/imports/local")
                        .retrieve()
                        .body(LocalImportSummaryView.class);
        assertThat(skippedRun).isNotNull();
        assertThat(skippedRun.accounts().getFirst().skipped()).isTrue();

        PlaceVisitStateView manualOverride =
                client.patch()
                        .uri("/api/v1/atlas/places/{id}/visit-status", eiffel.id())
                        .body(new UpdatePlaceVisitStatusRequest(VisitStatus.UNVISITED))
                        .retrieve()
                        .body(PlaceVisitStateView.class);
        assertThat(manualOverride).isNotNull();
        assertThat(manualOverride.status()).isEqualTo(VisitStatus.UNVISITED);
        assertThat(manualOverride.source()).isEqualTo(VisitSource.MANUAL_SINGLE);

        LocalImportSummaryView forcedRun =
                client.post()
                        .uri("/api/v1/imports/local?force=true")
                        .retrieve()
                        .body(LocalImportSummaryView.class);
        assertThat(forcedRun).isNotNull();
        assertThat(forcedRun.accounts().getFirst().skipped()).isFalse();

        AtlasPlaceView eiffelAfterForce =
                fetchAllPlaces(client).stream()
                        .filter(p -> p.id().equals(eiffel.id()))
                        .findFirst()
                        .orElseThrow();
        assertThat(eiffelAfterForce.visitStatus()).isEqualTo(VisitStatus.UNVISITED);
        assertThat(eiffelAfterForce.visitSource()).isEqualTo(VisitSource.MANUAL_SINGLE);
    }

    private static java.util.List<AtlasPlaceView> fetchAllPlaces(RestClient client) {
        AtlasPlacesResponse response =
                client.get().uri("/api/v1/atlas/places").retrieve().body(AtlasPlacesResponse.class);
        assertThat(response).isNotNull();
        return response.places();
    }

    private static AtlasPlaceView findPlaceByName(
            java.util.List<AtlasPlaceView> places, String name) {
        return places.stream()
                .filter(p -> p.canonicalName().equals(name))
                .findFirst()
                .orElseThrow();
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

    private static final class ListPlaceAssertions {

        private static void assertSingleCategory(
                java.util.List<AtlasPlaceView> places, PlaceCategory category, String subcategory) {
            assertThat(places).hasSize(1);
            assertThat(places.getFirst().category()).isEqualTo(category);
            assertThat(places.getFirst().subcategory()).isEqualTo(subcategory);
        }
    }
}
