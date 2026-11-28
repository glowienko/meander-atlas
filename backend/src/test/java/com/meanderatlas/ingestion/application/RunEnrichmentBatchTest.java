package com.meanderatlas.ingestion.application;

import static com.meanderatlas.ingestion.OpenCatalogTestData.properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.meanderatlas.ingestion.EnrichmentBatchResultView;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort;
import com.meanderatlas.ingestion.domain.source.EnrichmentJobRepository.PendingCoordinateJob;
import com.meanderatlas.ingestion.domain.source.ImportRecordKind;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.support.TransactionOperations;

class RunEnrichmentBatchTest {

    private static final UserId USER_ID =
            new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    void returnsEarlyWhenDisabled(@TempDir Path tempDir) {
        FakeAtlasPlaceRepository placeRepo = new FakeAtlasPlaceRepository();
        FakeEnrichmentJobRepository jobRepo = new FakeEnrichmentJobRepository();
        FakeOpenPoiCatalogPort catalogPort = new FakeOpenPoiCatalogPort();
        RunEnrichmentBatch disabledRunner =
                createRunner(tempDir, properties(false, 2, 2), placeRepo, jobRepo, catalogPort);

        PlaceId placeId = insertApproxPlace(placeRepo, "Wawel Castle");
        jobRepo.addPendingEnrichment(placeId);

        EnrichmentBatchResultView result = disabledRunner.runBatch(10, false);
        assertThat(result.enabled()).isFalse();
        assertThat(result.claimedCount()).isZero();
        assertThat(result.remainingPendingCount()).isEqualTo(1);
        assertThat(disabledRunner.startBackgroundDrainIfEnabled()).isFalse();
    }

    @Test
    void startBackgroundDrainIfEnabledSkipsEmptyQueueAndStartsWhenPending(@TempDir Path tempDir) {
        FakeAtlasPlaceRepository placeRepo = new FakeAtlasPlaceRepository();
        FakeEnrichmentJobRepository jobRepo = new FakeEnrichmentJobRepository();
        FakeOpenPoiCatalogPort catalogPort = new FakeOpenPoiCatalogPort();
        RunEnrichmentBatch enabledRunner =
                createRunner(tempDir, properties(true, 2, 2), placeRepo, jobRepo, catalogPort);

        assertThat(enabledRunner.startBackgroundDrainIfEnabled()).isFalse();

        PlaceId placeId = insertApproxPlace(placeRepo, "Wawel Castle");
        jobRepo.addPendingEnrichment(placeId);
        catalogPort.anchoredOutcomes.put(placeId, wawelMatch());

        assertThat(enabledRunner.startBackgroundDrainIfEnabled()).isTrue();
    }

    @Test
    void appliesMatchesSkipsMissesAndSchedulesRetryForTransientFailures(@TempDir Path tempDir) {
        FakeAtlasPlaceRepository placeRepo = new FakeAtlasPlaceRepository();
        FakeEnrichmentJobRepository jobRepo = new FakeEnrichmentJobRepository();
        FakeOpenPoiCatalogPort catalogPort = new FakeOpenPoiCatalogPort();

        PlaceId matchPlaceId = insertApproxPlace(placeRepo, "Wawel Royal Castle");
        UUID matchJobId = jobRepo.addPendingEnrichment(matchPlaceId);

        PlaceId retryPlaceId = insertApproxPlace(placeRepo, "Retry Spot");
        UUID retryJobId = jobRepo.addPendingEnrichment(retryPlaceId);

        PlaceId noMatchPlaceId = insertApproxPlace(placeRepo, "No Match Spot");
        UUID noMatchJobId = jobRepo.addPendingEnrichment(noMatchPlaceId);

        UUID unanchoredJobId = UUID.randomUUID();
        jobRepo.addPendingLookup(
                new PendingCoordinateJob(
                        unanchoredJobId,
                        UUID.randomUUID(),
                        "personal",
                        ImportRecordKind.SAVED_CSV,
                        "Colosseum",
                        null,
                        null,
                        null,
                        "{\"listName\":\"Want to go\"}"));

        UUID placeholderJobId = UUID.randomUUID();
        jobRepo.addPendingLookup(
                new PendingCoordinateJob(
                        placeholderJobId,
                        UUID.randomUUID(),
                        "personal",
                        ImportRecordKind.SAVED_CSV,
                        "Dropped Pin",
                        null,
                        null,
                        null,
                        "{\"listName\":\"Want to go\"}"));

        catalogPort.anchoredOutcomes.put(matchPlaceId, wawelMatch());
        catalogPort.anchoredOutcomes.put(
                retryPlaceId,
                new OpenPoiCatalogPort.EnrichmentLookupOutcome.TransientFailure("Overpass 429"));
        catalogPort.anchoredOutcomes.put(
                noMatchPlaceId,
                new OpenPoiCatalogPort.EnrichmentLookupOutcome.NoMatch("Not found"));
        catalogPort.unanchoredOutcomes.put(
                unanchoredJobId,
                new OpenPoiCatalogPort.EnrichmentLookupOutcome.Matched(
                        new OpenPoiCatalogPort.OpenPoiMatch(
                                new OpenPoiCatalogPort.OpenPoiCandidate(
                                        "way/202",
                                        "Colosseum",
                                        new GeoPoint(41.8902, 12.4922),
                                        Map.of("historic", "monument"),
                                        "Rome",
                                        "IT",
                                        null),
                                0.96,
                                0.0,
                                PlaceCategory.LANDMARK,
                                "monument",
                                "Rome",
                                null,
                                null)));

        RunEnrichmentBatch runner =
                createRunner(tempDir, properties(true, 2, 2), placeRepo, jobRepo, catalogPort);

        EnrichmentBatchResultView live = runner.runBatch(10, false);
        assertThat(live.dryRun()).isFalse();
        assertThat(live.claimedCount()).isEqualTo(5);
        assertThat(live.matchedCount()).isEqualTo(2);
        assertThat(live.upgradedCoordsCount()).isEqualTo(2);
        assertThat(live.upgradedCategoryCount()).isEqualTo(2);
        assertThat(live.photosAddedCount()).isEqualTo(1);
        assertThat(live.failedCount()).isEqualTo(1);
        assertThat(live.skippedCount()).isEqualTo(2);
        assertThat(live.remainingPendingCount()).isEqualTo(1);

        assertThat(jobRepo.statusOf(matchJobId)).isEqualTo("COMPLETED");
        assertThat(jobRepo.statusOf(retryJobId)).isEqualTo("FAILED");
        assertThat(jobRepo.statusOf(noMatchJobId)).isEqualTo("SKIPPED");
        assertThat(jobRepo.statusOf(unanchoredJobId)).isEqualTo("COMPLETED");
        assertThat(jobRepo.statusOf(placeholderJobId)).isEqualTo("SKIPPED");
        assertThat(placeRepo.place(matchPlaceId).coordSource).isEqualTo(CoordSource.OPEN_CATALOG);
        assertThat(placeRepo.place(matchPlaceId).openingHoursJson)
                .isEqualTo("{\"raw\":\"Mo-Su 09:00-17:00\"}");
        assertThat(placeRepo.placeCount()).isEqualTo(4);
    }

    @Test
    void enforcesSingleFlightGuardAndDrainsBackgroundAcrossMultipleBatches(@TempDir Path tempDir)
            throws InterruptedException {
        FakeAtlasPlaceRepository placeRepo = new FakeAtlasPlaceRepository();
        FakeEnrichmentJobRepository jobRepo = new FakeEnrichmentJobRepository();
        CountDownLatch enteredPort = new CountDownLatch(1);
        CountDownLatch releasePort = new CountDownLatch(1);

        PlaceId firstId = insertApproxPlace(placeRepo, "First Spot");
        PlaceId secondId = insertApproxPlace(placeRepo, "Second Spot");
        PlaceId thirdId = insertApproxPlace(placeRepo, "Third Spot");
        UUID firstJob = jobRepo.addPendingEnrichment(firstId);
        UUID secondJob = jobRepo.addPendingEnrichment(secondId);
        UUID thirdJob = jobRepo.addPendingEnrichment(thirdId);

        OpenPoiCatalogPort blockingPort =
                new OpenPoiCatalogPort() {
                    @Override
                    public Map<PlaceId, EnrichmentLookupOutcome> enrichAnchoredBatch(
                            List<AnchoredEnrichmentTarget> targets) {
                        enteredPort.countDown();
                        try {
                            releasePort.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                        return targets.stream()
                                .collect(
                                        java.util.stream.Collectors.toUnmodifiableMap(
                                                AnchoredEnrichmentTarget::placeId,
                                                t ->
                                                        new EnrichmentLookupOutcome.NoMatch(
                                                                "done " + t.canonicalName())));
                    }

                    @Override
                    public EnrichmentLookupOutcome resolveUnanchored(
                            UnanchoredLookupTarget target) {
                        return new EnrichmentLookupOutcome.NoMatch("none");
                    }
                };

        RunEnrichmentBatch runner =
                createRunner(tempDir, properties(true, 2, 1), placeRepo, jobRepo, blockingPort);

        EnrichmentBatchResultView started = runner.startDrain(10, false);
        assertThat(started.startedInBackground()).isTrue();
        assertThat(started.alreadyRunning()).isFalse();
        assertThat(enteredPort.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(runner.isRunning()).isTrue();

        EnrichmentBatchResultView concurrent = runner.runBatch(10, false);
        assertThat(concurrent.alreadyRunning()).isTrue();
        assertThat(concurrent.startedInBackground()).isFalse();

        releasePort.countDown();
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (runner.isRunning() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }

        assertThat(runner.isRunning()).isFalse();
        assertThat(jobRepo.statusOf(firstJob)).isEqualTo("SKIPPED");
        assertThat(jobRepo.statusOf(secondJob)).isEqualTo("SKIPPED");
        assertThat(jobRepo.statusOf(thirdJob)).isEqualTo("SKIPPED");
        runner.onShutdown();
    }

    @Test
    void failsClaimedJobsWithoutRefundingAttemptsAndClearsRunningFlagWhenCatalogPortThrows(
            @TempDir Path tempDir) {
        FakeAtlasPlaceRepository placeRepo = new FakeAtlasPlaceRepository();
        FakeEnrichmentJobRepository jobRepo = new FakeEnrichmentJobRepository();
        PlaceId placeId = insertApproxPlace(placeRepo, "Boom Spot");
        UUID jobId = jobRepo.addPendingEnrichment(placeId);

        RunEnrichmentBatch runner =
                createRunner(tempDir, properties(true, 2, 2), placeRepo, jobRepo, crashingPort());

        assertThatThrownBy(() -> runner.runBatch(5, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unexpected catalog crash");
        assertThat(runner.isRunning()).isFalse();
        assertThat(jobRepo.statusOf(jobId)).isEqualTo("FAILED");
        assertThat(jobRepo.attemptsOf(jobId)).isEqualTo(1);
        assertThat(jobRepo.lastErrorOf(jobId)).contains("unexpected catalog crash");
    }

    @Test
    void skipsPoisonJobOnceRetriesAreExhausted(@TempDir Path tempDir) {
        SteppingClock clock = new SteppingClock();
        FakeAtlasPlaceRepository placeRepo = new FakeAtlasPlaceRepository();
        FakeEnrichmentJobRepository jobRepo = new FakeEnrichmentJobRepository(clock);
        UUID jobId = jobRepo.addPendingEnrichment(insertApproxPlace(placeRepo, "Poison Spot"));
        RunEnrichmentBatch runner =
                createRunner(
                        tempDir, properties(true, 2, 2), placeRepo, jobRepo, crashingPort(), clock);

        assertThatThrownBy(() -> runner.runBatch(5, false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jobRepo.statusOf(jobId)).isEqualTo("FAILED");
        assertThat(runner.runBatch(5, false).claimedCount()).isZero();

        clock.advance(Duration.ofMinutes(1));
        assertThatThrownBy(() -> runner.runBatch(5, false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jobRepo.statusOf(jobId)).isEqualTo("SKIPPED");
        assertThat(jobRepo.attemptsOf(jobId)).isEqualTo(2);
    }

    @Test
    void failsClaimedJobsWhenThePersistTransactionThrows(@TempDir Path tempDir) {
        FakeAtlasPlaceRepository placeRepo = new FakeAtlasPlaceRepository();
        FakeEnrichmentJobRepository jobRepo = new FakeEnrichmentJobRepository();
        FakeOpenPoiCatalogPort catalogPort = new FakeOpenPoiCatalogPort();
        PlaceId placeId = insertApproxPlace(placeRepo, "Wawel Royal Castle");
        UUID jobId = jobRepo.addPendingEnrichment(placeId);
        catalogPort.anchoredOutcomes.put(placeId, wawelMatch());
        jobRepo.failCompletions();
        RunEnrichmentBatch runner =
                createRunner(tempDir, properties(true, 2, 2), placeRepo, jobRepo, catalogPort);

        assertThatThrownBy(() -> runner.runBatch(5, false)).hasMessage("persist failed");
        assertThat(runner.isRunning()).isFalse();
        assertThat(jobRepo.statusOf(jobId)).isEqualTo("FAILED");
        assertThat(jobRepo.attemptsOf(jobId)).isEqualTo(1);
    }

    private static OpenPoiCatalogPort.EnrichmentLookupOutcome wawelMatch() {
        return new OpenPoiCatalogPort.EnrichmentLookupOutcome.Matched(
                new OpenPoiCatalogPort.OpenPoiMatch(
                        new OpenPoiCatalogPort.OpenPoiCandidate(
                                "way/101",
                                "Wawel Royal Castle",
                                new GeoPoint(50.0541, 19.9355),
                                Map.of("historic", "castle"),
                                "Kraków",
                                "PL",
                                "Mo-Su 09:00-17:00"),
                        0.98,
                        15.0,
                        PlaceCategory.LANDMARK,
                        "castle",
                        "Kraków",
                        "https://upload.wikimedia.org/wikipedia/commons/wawel.jpg",
                        "Mo-Su 09:00-17:00"));
    }

    private static OpenPoiCatalogPort crashingPort() {
        return new OpenPoiCatalogPort() {
            @Override
            public Map<PlaceId, EnrichmentLookupOutcome> enrichAnchoredBatch(
                    List<AnchoredEnrichmentTarget> targets) {
                throw new IllegalStateException("unexpected catalog crash");
            }

            @Override
            public EnrichmentLookupOutcome resolveUnanchored(UnanchoredLookupTarget target) {
                return new EnrichmentLookupOutcome.NoMatch("unused");
            }
        };
    }

    private static PlaceId insertApproxPlace(FakeAtlasPlaceRepository placeRepo, String name) {
        return placeRepo.insertImportedPlace(
                USER_ID,
                name,
                new GeoPoint(50.0540, 19.9354),
                CoordSource.TAKEOUT_S2_APPROX,
                null,
                null,
                PlaceCategory.UNCATEGORIZED,
                null,
                CategorySource.HEURISTIC,
                DerivedRole.BACKLOG,
                "PL",
                null);
    }

    private static RunEnrichmentBatch createRunner(
            Path tempDir,
            EnrichmentProperties properties,
            FakeAtlasPlaceRepository placeRepo,
            FakeEnrichmentJobRepository jobRepo,
            OpenPoiCatalogPort catalogPort) {
        return createRunner(
                tempDir, properties, placeRepo, jobRepo, catalogPort, Clock.systemUTC());
    }

    private static RunEnrichmentBatch createRunner(
            Path tempDir,
            EnrichmentProperties properties,
            FakeAtlasPlaceRepository placeRepo,
            FakeEnrichmentJobRepository jobRepo,
            OpenPoiCatalogPort catalogPort,
            Clock clock) {
        CurrentUserProvider userProvider = () -> USER_ID;
        AtlasFacade atlasFacade =
                AtlasFacadeTestSupport.create(
                        placeRepo,
                        new NoOpBoundaryRepo(),
                        new NoOpStatsRepo(),
                        tempDir,
                        userProvider);
        return new RunEnrichmentBatch(
                properties,
                jobRepo,
                catalogPort,
                atlasFacade,
                userProvider,
                TransactionOperations.withoutTransaction(),
                new SimpleAsyncTaskExecutor("enrichment-test-"),
                clock);
    }

    private static final class SteppingClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class FakeOpenPoiCatalogPort implements OpenPoiCatalogPort {
        private final Map<PlaceId, EnrichmentLookupOutcome> anchoredOutcomes = new HashMap<>();
        private final Map<UUID, EnrichmentLookupOutcome> unanchoredOutcomes = new HashMap<>();

        @Override
        public Map<PlaceId, EnrichmentLookupOutcome> enrichAnchoredBatch(
                List<AnchoredEnrichmentTarget> targets) {
            return targets.stream()
                    .collect(
                            java.util.stream.Collectors.toUnmodifiableMap(
                                    AnchoredEnrichmentTarget::placeId,
                                    target ->
                                            anchoredOutcomes.getOrDefault(
                                                    target.placeId(),
                                                    new EnrichmentLookupOutcome.NoMatch("miss"))));
        }

        @Override
        public EnrichmentLookupOutcome resolveUnanchored(UnanchoredLookupTarget target) {
            return unanchoredOutcomes.getOrDefault(
                    target.jobId(), new EnrichmentLookupOutcome.NoMatch("miss"));
        }
    }

    private static final class NoOpBoundaryRepo implements RegionBoundaryRepository {
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

    private static final class NoOpStatsRepo implements AtlasStatsRepository {
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
