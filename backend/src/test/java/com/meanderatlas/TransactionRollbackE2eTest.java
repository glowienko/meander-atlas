package com.meanderatlas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meanderatlas.atlas.AtlasFacade;
import com.meanderatlas.atlas.ImportedPlaceUpsertCommand;
import com.meanderatlas.atlas.application.SeedRegionBoundaries;
import com.meanderatlas.atlas.domain.PlaceVisitState;
import com.meanderatlas.atlas.domain.boundaries.BoundaryTestData;
import com.meanderatlas.atlas.domain.boundaries.RegionBoundaryRepository;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.ingestion.application.ImportLocalDirectory;
import com.meanderatlas.ingestion.domain.TakeoutTestData;
import com.meanderatlas.ingestion.domain.source.AccountImportSource;
import com.meanderatlas.ingestion.domain.source.DiscoveredImportFile;
import com.meanderatlas.ingestion.domain.source.ImportFileKind;
import com.meanderatlas.ingestion.domain.source.ImportRecordKind;
import com.meanderatlas.ingestion.domain.source.ImportRepository;
import com.meanderatlas.ingestion.domain.source.ImportStatusSummary;
import com.meanderatlas.ingestion.domain.source.NewImportRecord;
import com.meanderatlas.ingestion.domain.source.NewResolutionJob;
import com.meanderatlas.ingestion.domain.source.TripImportSourcePort;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

@Tag("e2e")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransactionRollbackE2eTest {

    private static final UUID USER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UserId USER_ID = new UserId(USER_UUID);

    @Autowired private TransactionOperations transactionOperations;
    @Autowired private AtlasPlaceRepository placeRepository;
    @Autowired private RegionBoundaryRepository regionBoundaryRepository;
    @Autowired private ImportRepository importRepository;
    @Autowired private AtlasFacade atlasFacade;
    @Autowired private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanupState() {
        jdbc.update(
                "DELETE FROM import_batches WHERE user_id = :userId", Map.of("userId", USER_UUID));
        jdbc.update("DELETE FROM places WHERE user_id = :userId", Map.of("userId", USER_UUID));
        jdbc.update("DELETE FROM region_boundaries WHERE code LIKE 'TX-%'", Map.of());
    }

    @Test
    void transactionOperationsCommitsOnSuccessAndRollsBackPlaceWritesOnException() {
        Instant visitedAt = Instant.parse("2026-08-01T10:00:00Z");

        assertThatThrownBy(() ->
                        transactionOperations.executeWithoutResult(status -> {
                            PlaceId placeId = insertSamplePlaceWithMembershipAndVisit(visitedAt);
                            assertThat(placeRepository.existsPlaceForUser(USER_ID, placeId))
                                    .isTrue();
                            throw new IllegalStateException("Simulated place write failure");
                        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated place write failure");

        assertThat(countUserRows("places")).isZero();
        assertThat(countUserRows("place_memberships")).isZero();
        assertThat(countUserRows("place_visit_state")).isZero();

        PlaceId committedId =
                transactionOperations.execute(
                        status -> insertSamplePlaceWithMembershipAndVisit(visitedAt));

        assertThat(committedId).isNotNull();
        assertThat(countUserRows("places")).isEqualTo(1);
        assertThat(countUserRows("place_memberships")).isEqualTo(1);
        assertThat(countUserRows("place_visit_state")).isEqualTo(1);
    }

    @Test
    void seedRegionBoundariesRollsBackUpsertedFeaturesWhenBatchFailsMidTransaction(
            @TempDir Path tempDir) throws IOException {
        BoundaryTestData.writeGeoJson(
                tempDir,
                "ne_10m_admin_0_countries.geojson",
                """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "properties": {"ISO_A2_EH": "TX-VALID", "ADMIN": "Valid Testland"},
                      "geometry": {
                        "type": "Polygon",
                        "coordinates": [[[19.0, 49.5], [20.0, 49.5], [20.0, 50.5], [19.0, 50.5], [19.0, 49.5]]]
                      }
                    },
                    {
                      "type": "Feature",
                      "properties": {"ISO_A2_EH": "TX-BROKEN", "ADMIN": "Broken Testland"},
                      "geometry": {
                        "type": "Polygon",
                        "coordinates": ["not-a-ring"]
                      }
                    }
                  ]
                }
                """);

        SeedRegionBoundaries seeder =
                new SeedRegionBoundaries(
                        regionBoundaryRepository, tempDir.toString(), transactionOperations);

        assertThatThrownBy(() -> seeder.execute(true)).isInstanceOf(DataAccessException.class);

        Integer remainingTestBoundaries =
                jdbc.queryForObject(
                        "SELECT COUNT(*)::int FROM region_boundaries WHERE code LIKE 'TX-%'",
                        Map.of(), Integer.class);
        assertThat(remainingTestBoundaries).isZero();
    }

    @Test
    void transactionOperationsRollsBackCrossModuleIngestionAndAtlasWritesOnException() {
        UUID recordId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant observedAt = Instant.parse("2026-08-02T12:00:00Z");

        assertThatThrownBy(() ->
                        transactionOperations.executeWithoutResult(status -> {
                            UUID batchId =
                                    importRepository.startOrRestartBatch(
                                            USER_ID, "tx-account", "tx-sha", "LOCAL_DIRECTORY");
                            importRepository.insertRecords(
                                    batchId,
                                    USER_ID,
                                    "tx-account",
                                    List.of(
                                            new NewImportRecord(
                                                    recordId,
                                                    ImportRecordKind.SAVED_CSV,
                                                    "Want to go.csv",
                                                    "Tx Spot",
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    "{}")));
                            importRepository.enqueueCoordinateLookups(
                                    USER_ID, List.of(new NewResolutionJob(jobId, recordId)));
                            atlasFacade.upsertImportedPlace(
                                    new ImportedPlaceUpsertCommand(
                                            "Tx Castle",
                                            new GeoPoint(50.0540, 19.9354),
                                            CoordSource.TAKEOUT,
                                            null,
                                            999001L,
                                            PlaceCategory.LANDMARK,
                                            "castle",
                                            CategorySource.HEURISTIC,
                                            "PL",
                                            "Krakow",
                                            "tx-account",
                                            "Starred",
                                            ListKind.STARRED,
                                            "Should roll back",
                                            null,
                                            "https://maps.google.com/?cid=999001",
                                            observedAt,
                                            VisitStatus.VISITED,
                                            VisitSource.REVIEW,
                                            observedAt));
                            throw new IllegalStateException("Simulated cross-module failure");
                        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated cross-module failure");

        assertAllUserTablesEmpty();
    }

    @Test
    void importLocalDirectoryRollsBackEntireTwoPassImportOnCompletionFailure(@TempDir Path tempDir)
            throws IOException {
        Path savedJson = tempDir.resolve("Saved Places.json");
        Path savedCsv = tempDir.resolve("Want to go.csv");
        Files.writeString(
                savedJson,
                TakeoutTestData.savedPlaceGeoJson(
                        "Rollback Castle", 19.9354, 50.0540, 536870913L, "PL"),
                StandardCharsets.UTF_8);
        Files.writeString(
                savedCsv,
                TakeoutTestData.savedCsv(
                        "Rollback Castle,Matched via CID,"
                                + "https://www.google.com/maps/place/Rollback/data=!4m2!3m1!1s0x1:0x20000001,castle,",
                        "Unresolved Spot,Needs lookup,https://www.google.com/maps/search/?api=1&query=50,19,,"),
                StandardCharsets.UTF_8);

        AccountImportSource account =
                new AccountImportSource(
                        "tx-import",
                        "tx-import-sha",
                        List.of(
                                new DiscoveredImportFile(
                                        "Saved Places.json",
                                        ImportFileKind.STARRED_GEOJSON,
                                        savedJson),
                                new DiscoveredImportFile(
                                        "Want to go.csv", ImportFileKind.SAVED_CSV, savedCsv)));
        TripImportSourcePort sourcePort = filter -> List.of(account);
        ImportRepository failingOnComplete =
                new ImportRepository() {
                    @Override
                    public boolean hasCompletedBatch(
                            UserId userId, String accountLabel, String contentSha256) {
                        return importRepository.hasCompletedBatch(
                                userId, accountLabel, contentSha256);
                    }

                    @Override
                    public UUID startOrRestartBatch(
                            UserId userId,
                            String accountLabel,
                            String contentSha256,
                            String sourceType) {
                        return importRepository.startOrRestartBatch(
                                userId, accountLabel, contentSha256, sourceType);
                    }

                    @Override
                    public void insertRecords(
                            UUID batchId,
                            UserId userId,
                            String accountLabel,
                            List<NewImportRecord> records) {
                        importRepository.insertRecords(batchId, userId, accountLabel, records);
                    }

                    @Override
                    public void enqueueCoordinateLookups(
                            UserId userId, List<NewResolutionJob> jobs) {
                        importRepository.enqueueCoordinateLookups(userId, jobs);
                    }

                    @Override
                    public void markBatchCompleted(UUID batchId, int recordCount) {
                        throw new IllegalStateException("Simulated failure on completion");
                    }

                    @Override
                    public ImportStatusSummary fetchStatus(UserId userId) {
                        return importRepository.fetchStatus(userId);
                    }
                };

        ImportLocalDirectory useCase =
                new ImportLocalDirectory(
                        sourcePort,
                        failingOnComplete,
                        atlasFacade,
                        () -> USER_ID,
                        transactionOperations);

        assertThatThrownBy(() -> useCase.execute(null, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated failure on completion");

        assertAllUserTablesEmpty();
    }

    private PlaceId insertSamplePlaceWithMembershipAndVisit(Instant visitedAt) {
        PlaceId placeId =
                placeRepository.insertImportedPlace(
                        USER_ID,
                        "Tx Place",
                        new GeoPoint(50.0540, 19.9354),
                        CoordSource.TAKEOUT,
                        null,
                        null,
                        PlaceCategory.LANDMARK,
                        "castle",
                        CategorySource.HEURISTIC,
                        DerivedRole.MUST_SEE,
                        "PL",
                        "Krakow");
        placeRepository.upsertMembership(
                USER_ID,
                placeId,
                "tx-account",
                "Starred",
                ListKind.STARRED,
                "Tx note",
                null,
                null,
                visitedAt);
        placeRepository.upsertVisitStates(
                List.of(
                        PlaceVisitState.unvisited(placeId, USER_ID)
                                .apply(VisitStatus.VISITED, VisitSource.MANUAL_SINGLE, visitedAt)));
        return placeId;
    }

    private void assertAllUserTablesEmpty() {
        assertThat(countUserRows("import_batches")).isZero();
        assertThat(countUserRows("import_records")).isZero();
        assertThat(countUserRows("resolution_jobs")).isZero();
        assertThat(countUserRows("places")).isZero();
        assertThat(countUserRows("place_memberships")).isZero();
        assertThat(countUserRows("place_visit_state")).isZero();
    }

    private int countUserRows(String tableName) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*)::int FROM " + tableName + " WHERE user_id = :userId",
                        Map.of("userId", USER_UUID),
                        Integer.class);
        return count == null ? 0 : count;
    }
}
