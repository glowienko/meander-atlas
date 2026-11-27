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
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.ingestion.domain.FixtureArchiveSupport;
import com.meanderatlas.ingestion.domain.TakeoutTestData;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.UserId;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
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

    @Autowired private AtlasPlaceRepository placeRepository;

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

        ImportStatusView initialStatus =
                client.get().uri("/api/v1/imports/status").retrieve().body(ImportStatusView.class);
        assertThat(initialStatus).isNotNull();
        assertThat(initialStatus.pendingResolutionCount()).isZero();
        assertThat(initialStatus.completedBatches()).isZero();
        assertThat(initialStatus.accounts()).isEmpty();
        assertThat(initialStatus.lastImportedAt()).isNull();

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
                triageDir.resolve("Reviews.json"),
                """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "geometry": {
                        "type": "Point",
                        "coordinates": [19.935412, 50.054230]
                      },
                      "properties": {
                        "date": "2026-07-01T10:00:00Z",
                        "google_maps_url": "http://maps.google.com/?cid=536870920",
                        "review_text_published": "Closer visited spot",
                        "location": { "name": "Closer Visited Spot", "country_code": "PL" }
                      }
                    }
                  ]
                }
                """,
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
        assertThat(summary.totalPlacesCreated()).isEqualTo(2);
        assertThat(summary.totalTimelineMarkedTriage()).isEqualTo(1);
        List<AtlasPlaceView> triageRunPlaces = fetchAllPlaces(restClient());
        assertThat(findPlaceByName(triageRunPlaces, "Nearby Triage Landmark").visitStatus())
                .isEqualTo(VisitStatus.NEEDS_TRIAGE);
        assertThat(findPlaceByName(triageRunPlaces, "Closer Visited Spot").visitStatus())
                .isEqualTo(VisitStatus.VISITED);
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

        assertSingleCategory(fetchAllPlaces(client), PlaceCategory.MUSEUM, "museum");
    }

    @Test
    void crossMatchesAcrossAccountsHandlesCoordlessReviewsAndRejectsGoogleTempWithoutExpiry()
            throws IOException {
        Path accountADir = tempRoot.resolve("imports/account-a");
        Path accountBDir = tempRoot.resolve("imports/account-b");
        Files.createDirectories(accountADir);
        Files.createDirectories(accountBDir);

        Files.writeString(
                accountADir.resolve("Want to go.csv"),
                TakeoutTestData.savedCsv(
                        "Shared Castle,Great view,"
                                + "https://www.google.com/maps/place/Shared+Castle/data=!4m2!3m1!1s0x1:0x20000012,castle,"),
                StandardCharsets.UTF_8);
        Files.writeString(
                accountADir.resolve("Reviews.json"),
                """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "geometry": null,
                      "properties": {
                        "date": "2026-07-01T10:00:00Z",
                        "google_maps_url": "http://maps.google.com/?cid=536870930",
                        "review_text_published": "Coordless CID review",
                        "location": { "name": "Shared Castle" }
                      }
                    },
                    {
                      "type": "Feature",
                      "geometry": null,
                      "properties": {
                        "date": "2026-07-02T10:00:00Z",
                        "review_text_published": "Coordless unresolved review",
                        "location": { "name": "Unresolved Spot" }
                      }
                    }
                  ]
                }
                """,
                StandardCharsets.UTF_8);
        Files.writeString(
                accountBDir.resolve("Saved Places.json"),
                TakeoutTestData.savedPlaceGeoJson(
                        "Shared Castle", 19.9354, 50.0540, 536870930L, "PL"),
                StandardCharsets.UTF_8);

        LocalImportSummaryView summary =
                restClient()
                        .post()
                        .uri("/api/v1/imports/local")
                        .retrieve()
                        .body(LocalImportSummaryView.class);
        assertThat(summary).isNotNull();
        assertThat(summary.totalPlacesCreated()).isEqualTo(1);
        assertThat(summary.totalPlacesMerged()).isEqualTo(1);
        assertThat(summary.totalCsvCidMatched()).isEqualTo(1);
        assertThat(summary.totalQueuedResolutionJobs()).isEqualTo(1);

        List<AtlasPlaceView> places = fetchAllPlaces(restClient());
        assertThat(places).hasSize(1);
        AtlasPlaceView shared = places.getFirst();
        assertThat(shared.visitStatus()).isEqualTo(VisitStatus.VISITED);
        assertThat(shared.visitSource()).isEqualTo(VisitSource.REVIEW);
        assertThat(shared.memberships()).hasSize(3);

        Integer pendingJobs =
                jdbc.queryForObject(
                        "SELECT COUNT(*)::int FROM resolution_jobs WHERE place_id IS NULL AND job_type = 'COORDINATE_LOOKUP'",
                        Map.of(),
                        Integer.class);
        assertThat(pendingJobs).isEqualTo(1);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        placeRepository.insertImportedPlace(
                                new UserId(USER_ID),
                                "Ephemeral Place",
                                new GeoPoint(50.0, 19.9),
                                CoordSource.GOOGLE_TEMP,
                                null,
                                null,
                                PlaceCategory.LANDMARK,
                                null,
                                CategorySource.HEURISTIC,
                                DerivedRole.BACKLOG,
                                "PL",
                                null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void materializesPendingCoordinateJobsOfflineAndUpgradesS2ApproxOnExactMerge()
            throws IOException {
        Path s2AccountDir = tempRoot.resolve("imports/s2-first");
        Path exactAccountDir = tempRoot.resolve("imports/exact-second");
        Files.createDirectories(s2AccountDir);
        Files.createDirectories(exactAccountDir);

        Files.writeString(
                s2AccountDir.resolve("Want to go.csv"),
                TakeoutTestData.savedCsv(
                        "Wawel S2 First,Castle,"
                                + "https://www.google.com/maps/place/Wawel/data=!4m2!3m1!1s0x47165b120455da67:0x20000099,castle,"),
                StandardCharsets.UTF_8);

        RestClient client = restClient();
        LocalImportSummaryView s2Import =
                client.post()
                        .uri("/api/v1/imports/local?account=s2-first")
                        .retrieve()
                        .body(LocalImportSummaryView.class);
        assertThat(s2Import).isNotNull();
        assertThat(s2Import.totalPlacesCreated()).isEqualTo(1);
        assertThat(s2Import.totalQueuedResolutionJobs()).isZero();

        AtlasPlaceView s2BeforeMerge = findPlaceByName(fetchAllPlaces(client), "Wawel S2 First");
        assertThat(s2BeforeMerge.coordSource()).isEqualTo(CoordSource.TAKEOUT_S2_APPROX);

        Files.writeString(
                exactAccountDir.resolve("Saved Places.json"),
                TakeoutTestData.savedPlaceGeoJson(
                        "Wawel Exact Second", 19.935412, 50.054049, 536871065L, "PL"),
                StandardCharsets.UTF_8);
        LocalImportSummaryView exactMergeImport =
                client.post()
                        .uri("/api/v1/imports/local?account=exact-second")
                        .retrieve()
                        .body(LocalImportSummaryView.class);
        assertThat(exactMergeImport).isNotNull();
        assertThat(exactMergeImport.totalPlacesMerged()).isEqualTo(1);

        AtlasPlaceView upgradedPlace = findPlaceByName(fetchAllPlaces(client), "Wawel S2 First");
        assertThat(upgradedPlace.coordSource()).isEqualTo(CoordSource.TAKEOUT);
        assertThat(upgradedPlace.lat()).isEqualTo(50.054049);
        assertThat(upgradedPlace.lng()).isEqualTo(19.935412);

        UUID batchId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO import_batches
                    (id, user_id, account_label, content_sha256, source_type, status, record_count)
                VALUES
                    (:id, :userId, 'legacy-batch', 'legacy-sha', 'LOCAL_DIRECTORY', 'COMPLETED', 4)
                """,
                Map.of("id", batchId, "userId", USER_ID));

        UUID cidJobId =
                insertPendingCsvLookupJob(
                        batchId,
                        "Wawel Legacy CID",
                        "https://www.google.com/maps/place/Wawel/data=!4m2!3m1!1s0x1:0x20000099",
                        536871065L,
                        "{\"listName\":\"Favorites\",\"listKind\":\"FAVORITE\",\"note\":\"Backfilled\"}");
        UUID dmsJobId =
                insertPendingCsvLookupJob(
                        batchId,
                        "47°55'12.3\"N 106°54'19.8\"E",
                        "https://www.google.com/maps/search/?api=1&query=47.92,106.90",
                        null,
                        "{\"listName\":\"Want to go\",\"listKind\":\"WANT_TO_GO\"}");
        UUID s2JobId =
                insertPendingCsvLookupJob(
                        batchId,
                        "Miradouro da Senhora",
                        "https://www.google.com/maps/place/Lima/data=!4m2!3m1!1s0x9105c8cdc0000000:0x30000011",
                        null,
                        "{\"listName\":\"Custom Lisbon\"}");
        UUID stubJobId =
                insertPendingCsvLookupJob(
                        batchId,
                        "Unresolvable Legacy Stub",
                        "https://www.google.com/maps/place/Stub/data=!4m2!3m1!1s0x10000001:0x0",
                        null,
                        "{\"listName\":\"Want to go\"}");

        OfflineMaterializationSummaryView first =
                client.post()
                        .uri("/api/v1/imports/materialize-offline")
                        .retrieve()
                        .body(OfflineMaterializationSummaryView.class);
        assertThat(first).isNotNull();
        assertThat(first.materializedCount()).isEqualTo(3);
        assertThat(first.exactFromTitleCount()).isEqualTo(1);
        assertThat(first.approxFromS2Count()).isEqualTo(1);
        assertThat(first.mergedByCidCount()).isEqualTo(1);
        assertThat(first.remainingLookupCount()).isEqualTo(1);
        assertThat(first.enqueuedEnrichmentCount()).isEqualTo(2);

        List<AtlasPlaceView> placesAfter = fetchAllPlaces(client);
        assertThat(findPlaceByName(placesAfter, "47°55'12.3\"N 106°54'19.8\"E").coordSource())
                .isEqualTo(CoordSource.TAKEOUT);
        assertThat(findPlaceByName(placesAfter, "Miradouro da Senhora").coordSource())
                .isEqualTo(CoordSource.TAKEOUT_S2_APPROX);
        assertThat(findPlaceByName(placesAfter, "Miradouro da Senhora").category())
                .isEqualTo(PlaceCategory.VIEWPOINT);

        Integer completedCount =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*)::int
                        FROM resolution_jobs
                        WHERE id IN (:ids)
                          AND status = 'COMPLETED'
                          AND place_id IS NOT NULL
                        """,
                        Map.of("ids", List.of(cidJobId, dmsJobId, s2JobId)),
                        Integer.class);
        assertThat(completedCount).isEqualTo(3);

        Integer stubPending =
                jdbc.queryForObject(
                        "SELECT COUNT(*)::int FROM resolution_jobs WHERE id = :id AND status = 'PENDING'",
                        Map.of("id", stubJobId),
                        Integer.class);
        assertThat(stubPending).isEqualTo(1);

        jdbc.update(
                """
                UPDATE resolution_jobs
                SET status = 'COMPLETED'
                WHERE user_id = :userId
                  AND place_id = :placeId
                  AND job_type = 'OPEN_CATALOG_ENRICH'
                """,
                Map.of("userId", USER_ID, "placeId", upgradedPlace.id()));

        OfflineMaterializationSummaryView second =
                client.post()
                        .uri("/api/v1/imports/materialize-offline")
                        .retrieve()
                        .body(OfflineMaterializationSummaryView.class);
        assertThat(second).isNotNull();
        assertThat(second.materializedCount()).isZero();
        assertThat(second.enqueuedEnrichmentCount()).isZero();
        assertThat(second.remainingLookupCount()).isEqualTo(1);
    }

    private UUID insertPendingCsvLookupJob(
            UUID batchId, String title, String url, @Nullable Long googleCid, String payloadJson) {
        UUID recordId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO import_records (
                    id, batch_id, user_id, account_label, record_type, source_file,
                    raw_title, raw_url, google_cid, payload_json
                )
                VALUES (
                    :id, :batchId, :userId, 'legacy', 'SAVED_CSV', 'Want to go.csv',
                    :rawTitle, :rawUrl, :googleCid, CAST(:payloadJson AS jsonb)
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", recordId)
                        .addValue("batchId", batchId)
                        .addValue("userId", USER_ID)
                        .addValue("rawTitle", title)
                        .addValue("rawUrl", url)
                        .addValue("googleCid", googleCid, Types.BIGINT)
                        .addValue("payloadJson", payloadJson));
        jdbc.update(
                """
                INSERT INTO resolution_jobs
                    (id, user_id, import_record_id, place_id, job_type, status, attempts)
                VALUES
                    (:id, :userId, :importRecordId, NULL, 'COORDINATE_LOOKUP', 'PENDING', 0)
                """,
                Map.of("id", jobId, "userId", USER_ID, "importRecordId", recordId));
        return jobId;
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
        List<AtlasPlaceView> places = fetchAllPlaces(client);
        assertThat(places).hasSize(5);

        AtlasPlaceView wawel = findPlaceByName(places, "Wawel Royal Castle");
        assertThat(wawel.visitStatus()).isEqualTo(VisitStatus.VISITED);
        assertThat(wawel.visitSource()).isEqualTo(VisitSource.REVIEW);
        assertThat(wawel.countryCode()).isEqualTo("PL");
        assertThat(wawel.memberships()).hasSize(3);

        AtlasPlaceView eiffel = findPlaceByName(places, "Eiffel Tower");
        assertThat(eiffel.visitStatus()).isEqualTo(VisitStatus.VISITED);
        assertThat(eiffel.visitSource()).isEqualTo(VisitSource.TIMELINE);
        assertThat(eiffel.visitCount()).isEqualTo(1);

        LocalImportSummaryView forcedIdempotentRun =
                client.post()
                        .uri("/api/v1/imports/local?force=true")
                        .retrieve()
                        .body(LocalImportSummaryView.class);
        assertThat(forcedIdempotentRun).isNotNull();
        assertThat(forcedIdempotentRun.totalTimelineMatchedVisited()).isEqualTo(5);

        AtlasPlaceView eiffelAfterIdempotentForce =
                findPlaceByName(fetchAllPlaces(client), "Eiffel Tower");
        assertThat(eiffelAfterIdempotentForce.visitCount()).isEqualTo(1);

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
        assertThat(forcedRun.totalTimelineMatchedVisited()).isEqualTo(4);

        AtlasPlaceView eiffelAfterForce =
                fetchAllPlaces(client).stream()
                        .filter(p -> p.id().equals(eiffel.id()))
                        .findFirst()
                        .orElseThrow();
        assertThat(eiffelAfterForce.visitStatus()).isEqualTo(VisitStatus.UNVISITED);
        assertThat(eiffelAfterForce.visitSource()).isEqualTo(VisitSource.MANUAL_SINGLE);
    }

    private static List<AtlasPlaceView> fetchAllPlaces(RestClient client) {
        AtlasPlacesResponse response =
                client.get().uri("/api/v1/atlas/places").retrieve().body(AtlasPlacesResponse.class);
        assertThat(response).isNotNull();
        return response.places();
    }

    private static AtlasPlaceView findPlaceByName(List<AtlasPlaceView> places, String name) {
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

    private static void assertSingleCategory(
            List<AtlasPlaceView> places, PlaceCategory category, String subcategory) {
        assertThat(places).hasSize(1);
        assertThat(places.getFirst().category()).isEqualTo(category);
        assertThat(places.getFirst().subcategory()).isEqualTo(subcategory);
    }
}
