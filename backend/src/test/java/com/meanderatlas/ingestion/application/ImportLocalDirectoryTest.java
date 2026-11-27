package com.meanderatlas.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.meanderatlas.atlas.AtlasFacade;
import com.meanderatlas.atlas.application.AtlasFacadeTestSupport;
import com.meanderatlas.atlas.application.FakeAtlasPlaceRepository;
import com.meanderatlas.atlas.domain.RegionLevel;
import com.meanderatlas.atlas.domain.boundaries.BoundaryFeature;
import com.meanderatlas.atlas.domain.boundaries.RegionBoundaryRepository;
import com.meanderatlas.atlas.domain.stats.AtlasStats;
import com.meanderatlas.atlas.domain.stats.AtlasStatsRepository;
import com.meanderatlas.atlas.domain.stats.RegionSummary;
import com.meanderatlas.atlas.domain.stats.RegionSummaryFilter;
import com.meanderatlas.ingestion.AccountImportSummaryView;
import com.meanderatlas.ingestion.LocalImportSummaryView;
import com.meanderatlas.ingestion.domain.TakeoutTestData;
import com.meanderatlas.ingestion.domain.source.AccountImportSource;
import com.meanderatlas.ingestion.domain.source.DiscoveredImportFile;
import com.meanderatlas.ingestion.domain.source.ImportFileKind;
import com.meanderatlas.ingestion.domain.source.ImportRepository;
import com.meanderatlas.ingestion.domain.source.ImportStatusSummary;
import com.meanderatlas.ingestion.domain.source.NewImportRecord;
import com.meanderatlas.ingestion.domain.source.NewResolutionJob;
import com.meanderatlas.ingestion.domain.source.TripImportSourcePort;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionOperations;

class ImportLocalDirectoryTest {

    private static final UserId USER_ID =
            new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    void performsTwoPassCrossAccountCidMatchingCoordlessReviewsAndIdempotentTimelineReimport(
            @TempDir Path tempDir) throws IOException {
        Path alphaCsv = tempDir.resolve("alpha/Want to go.csv");
        Path alphaReviews = tempDir.resolve("alpha/Reviews.json");
        Path betaSaved = tempDir.resolve("beta/Saved Places.json");
        Path betaTimeline = tempDir.resolve("beta/Timeline.json");
        Files.createDirectories(alphaCsv.getParent());
        Files.createDirectories(betaSaved.getParent());

        Files.writeString(
                alphaCsv,
                TakeoutTestData.savedCsv(
                        "Cross Account Place,Museum notes,"
                                + "https://www.google.com/maps/place/Cross/data=!4m2!3m1!1s0x1:0x20000001,museum,"),
                StandardCharsets.UTF_8);
        Files.writeString(
                alphaReviews,
                """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "geometry": null,
                      "properties": {
                        "date": "2026-07-01T10:00:00Z",
                        "google_maps_url": "http://maps.google.com/?cid=536870913",
                        "review_text_published": "Coord-less review matched by CID",
                        "location": { "name": "Cross Account Place" }
                      }
                    },
                    {
                      "type": "Feature",
                      "geometry": null,
                      "properties": {
                        "date": "2026-07-02T10:00:00Z",
                        "review_text_published": "Coord-less review needing lookup",
                        "location": { "name": "Unknown Review Spot" }
                      }
                    }
                  ]
                }
                """,
                StandardCharsets.UTF_8);
        Files.writeString(
                betaSaved,
                TakeoutTestData.savedPlaceGeoJson(
                        "Cross Account Place", 19.9354, 50.0540, 536870913L, "PL"),
                StandardCharsets.UTF_8);
        Files.writeString(
                betaTimeline,
                TakeoutTestData.singleVisitTimelineJson(
                        "2026-08-10T09:00:00Z", "2026-08-10T10:00:00Z", 50.0540, 19.9354),
                StandardCharsets.UTF_8);

        List<AccountImportSource> sources =
                List.of(
                        new AccountImportSource(
                                "alpha",
                                "sha-alpha",
                                List.of(
                                        new DiscoveredImportFile(
                                                "Want to go.csv",
                                                ImportFileKind.SAVED_CSV,
                                                alphaCsv),
                                        new DiscoveredImportFile(
                                                "Reviews.json",
                                                ImportFileKind.REVIEW_GEOJSON,
                                                alphaReviews))),
                        new AccountImportSource(
                                "beta",
                                "sha-beta",
                                List.of(
                                        new DiscoveredImportFile(
                                                "Saved Places.json",
                                                ImportFileKind.STARRED_GEOJSON,
                                                betaSaved),
                                        new DiscoveredImportFile(
                                                "Timeline.json",
                                                ImportFileKind.TIMELINE_JSON,
                                                betaTimeline))));

        FakeAtlasPlaceRepository placeRepo = new FakeAtlasPlaceRepository();
        FakeImportRepository importRepo = new FakeImportRepository();
        FakeEnrichmentJobRepository enrichmentJobRepo = new FakeEnrichmentJobRepository();
        ImportLocalDirectory useCase =
                createUseCase(tempDir, sources, placeRepo, importRepo, enrichmentJobRepo);

        LocalImportSummaryView firstRun = useCase.execute(null, false);

        assertThat(firstRun.accounts()).hasSize(2);
        AccountImportSummaryView alpha = firstRun.accounts().getFirst();
        AccountImportSummaryView beta = firstRun.accounts().get(1);
        assertThat(alpha.accountLabel()).isEqualTo("alpha");
        assertThat(alpha.placesMerged()).isEqualTo(1);
        assertThat(alpha.csvCidMatched()).isEqualTo(1);
        assertThat(alpha.queuedResolutionJobs()).isEqualTo(1);
        assertThat(beta.placesCreated()).isEqualTo(1);
        assertThat(beta.timelineMatchedVisited()).isEqualTo(1);
        assertThat(importRepo.jobs).hasSize(1);

        PlaceId createdPlaceId = placeRepo.findByGoogleCid(USER_ID, 536870913L).orElseThrow().id();
        assertThat(enrichmentJobRepo.enqueuedEnrichmentPlaceIds()).containsExactly(createdPlaceId);
        assertThat(placeRepo.visitState(createdPlaceId).status()).isEqualTo(VisitStatus.VISITED);
        assertThat(placeRepo.visitState(createdPlaceId).source()).isEqualTo(VisitSource.REVIEW);
        assertThat(placeRepo.visitState(createdPlaceId).visitCount()).isEqualTo(1);

        LocalImportSummaryView skippedRun = useCase.execute(null, false);
        assertThat(skippedRun.accounts()).allMatch(AccountImportSummaryView::skipped);

        LocalImportSummaryView forcedRun = useCase.execute(null, true);
        assertThat(forcedRun.accounts()).noneMatch(AccountImportSummaryView::skipped);
        assertThat(placeRepo.visitState(createdPlaceId).visitCount()).isEqualTo(1);
    }

    @Test
    void materializesCsvRowsOfflineViaTitleCoordsAndS2CellWithCidDedup(@TempDir Path tempDir)
            throws IOException {
        Path wantToGoCsv = tempDir.resolve("personal/Want to go.csv");
        Path favoritesCsv = tempDir.resolve("personal/Favorites.csv");
        Files.createDirectories(wantToGoCsv.getParent());

        Files.writeString(
                wantToGoCsv,
                TakeoutTestData.savedCsv(
                        "47°55'12.3\"N 106°54'19.8\"E,Dms camp,"
                                + "https://www.google.com/maps/place/Camp/data=!4m2!3m1!1s0x10000001:0x999001,,",
                        "Wawel Approx S2,Castle note,"
                                + "https://www.google.com/maps/place/Wawel/data=!4m2!3m1!1s0x47165b120455da67:0x999002,castle,",
                        "No Hint Spot,Needs lookup,"
                                + "https://www.google.com/maps/search/?api=1&query=50,19,,"),
                StandardCharsets.UTF_8);
        Files.writeString(
                favoritesCsv,
                TakeoutTestData.savedCsv(
                        "Wawel Approx S2 Duplicate,Duplicate list,"
                                + "https://www.google.com/maps/place/Wawel/data=!4m2!3m1!1s0x47165b120455da67:0x999002,,"),
                StandardCharsets.UTF_8);

        List<AccountImportSource> sources =
                List.of(
                        new AccountImportSource(
                                "personal",
                                "sha-stage-a",
                                List.of(
                                        new DiscoveredImportFile(
                                                "Favorites.csv",
                                                ImportFileKind.SAVED_CSV,
                                                favoritesCsv),
                                        new DiscoveredImportFile(
                                                "Want to go.csv",
                                                ImportFileKind.SAVED_CSV,
                                                wantToGoCsv))));

        FakeAtlasPlaceRepository placeRepo = new FakeAtlasPlaceRepository();
        FakeImportRepository importRepo = new FakeImportRepository();
        FakeEnrichmentJobRepository enrichmentJobRepo = new FakeEnrichmentJobRepository();
        ImportLocalDirectory useCase =
                createUseCase(tempDir, sources, placeRepo, importRepo, enrichmentJobRepo);

        LocalImportSummaryView summary = useCase.execute(null, false);

        assertThat(summary.totalPlacesCreated()).isEqualTo(2);
        assertThat(summary.totalCsvCidMatched()).isEqualTo(1);
        assertThat(summary.totalQueuedResolutionJobs()).isEqualTo(1);
        assertThat(importRepo.jobs).hasSize(1);

        PlaceId dmsPlaceId =
                placeRepo
                        .findByGoogleCid(USER_ID, Long.parseUnsignedLong("999001", 16))
                        .orElseThrow()
                        .id();
        PlaceId s2PlaceId =
                placeRepo
                        .findByGoogleCid(USER_ID, Long.parseUnsignedLong("999002", 16))
                        .orElseThrow()
                        .id();
        assertThat(placeRepo.place(dmsPlaceId).coordSource).isEqualTo(CoordSource.TAKEOUT);
        assertThat(placeRepo.place(s2PlaceId).coordSource).isEqualTo(CoordSource.TAKEOUT_S2_APPROX);
        assertThat(placeRepo.place(s2PlaceId).category).isEqualTo(PlaceCategory.LANDMARK);
        assertThat(placeRepo.membershipsOf(s2PlaceId)).hasSize(2);
        assertThat(enrichmentJobRepo.enqueuedEnrichmentPlaceIds())
                .containsExactlyInAnyOrder(dmsPlaceId, s2PlaceId);
    }

    private static ImportLocalDirectory createUseCase(
            Path tempDir,
            List<AccountImportSource> sources,
            FakeAtlasPlaceRepository placeRepo,
            FakeImportRepository importRepo,
            FakeEnrichmentJobRepository enrichmentJobRepo) {
        CurrentUserProvider userProvider = () -> USER_ID;
        TripImportSourcePort sourcePort =
                filter ->
                        filter == null
                                ? sources
                                : sources.stream()
                                        .filter(s -> s.accountLabel().equals(filter))
                                        .toList();
        RegionBoundaryRepository boundaryRepo = new NoOpRegionBoundaryRepository();
        AtlasStatsRepository statsRepo = new NoOpAtlasStatsRepository();
        AtlasFacade atlasFacade =
                AtlasFacadeTestSupport.create(
                        placeRepo, boundaryRepo, statsRepo, tempDir, userProvider);
        return new ImportLocalDirectory(
                sourcePort,
                importRepo,
                enrichmentJobRepo,
                atlasFacade,
                userProvider,
                TransactionOperations.withoutTransaction());
    }

    private static final class FakeImportRepository implements ImportRepository {

        private final Map<String, UUID> completedBatches = new HashMap<>();
        private final List<NewResolutionJob> jobs = new ArrayList<>();

        @Override
        public boolean hasCompletedBatch(UserId userId, String accountLabel, String contentSha256) {
            return completedBatches.containsKey(accountLabel + ":" + contentSha256);
        }

        @Override
        public UUID startOrRestartBatch(
                UserId userId, String accountLabel, String contentSha256, String sourceType) {
            UUID batchId = UUID.randomUUID();
            completedBatches.put(accountLabel + ":" + contentSha256, batchId);
            return batchId;
        }

        @Override
        public void insertRecords(
                UUID batchId, UserId userId, String accountLabel, List<NewImportRecord> records) {}

        @Override
        public void enqueueCoordinateLookups(UserId userId, List<NewResolutionJob> newJobs) {
            jobs.addAll(newJobs);
        }

        @Override
        public void markBatchCompleted(UUID batchId, int recordCount) {}

        @Override
        public ImportStatusSummary fetchStatus(UserId userId) {
            return new ImportStatusSummary(jobs.size(), completedBatches.size(), List.of(), null);
        }
    }

    private static final class NoOpRegionBoundaryRepository implements RegionBoundaryRepository {
        @Override
        public int upsertBoundaries(List<BoundaryFeature> features, double simplifyTolerance) {
            return 0;
        }

        @Override
        public long countByLevel(RegionLevel level) {
            return 0;
        }

        @Override
        public int assignRegionsToUntaggedPlaces() {
            return 0;
        }

        @Override
        public byte[] fetchRegionMvtTile(
                RegionLevel level, int z, int x, int y, @Nullable String countryCode) {
            return new byte[0];
        }
    }

    private static final class NoOpAtlasStatsRepository implements AtlasStatsRepository {
        @Override
        public List<RegionSummary> findRegionSummaries(
                UserId userId,
                RegionLevel level,
                @Nullable String countryCode,
                RegionSummaryFilter filter) {
            return List.of();
        }

        @Override
        public Optional<RegionSummary> findRegionSummary(
                UserId userId, RegionLevel level, String code) {
            return Optional.empty();
        }

        @Override
        public AtlasStats fetchStats(UserId userId) {
            return new AtlasStats(0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of());
        }
    }
}
