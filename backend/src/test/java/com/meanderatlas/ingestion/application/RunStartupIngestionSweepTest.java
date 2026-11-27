package com.meanderatlas.ingestion.application;

import static com.meanderatlas.ingestion.OpenCatalogTestData.properties;
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
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort;
import com.meanderatlas.ingestion.domain.source.EnrichmentJobRepository.PendingCoordinateJob;
import com.meanderatlas.ingestion.domain.source.ImportRecordKind;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionOperations;

class RunStartupIngestionSweepTest {

    private static final UserId USER_ID =
            new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    void materializesLegacyPendingCoordinateJobsAndResumesBackgroundEnrichmentWhenEnabled(
            @TempDir Path tempDir) {
        FakeAtlasPlaceRepository placeRepo = new FakeAtlasPlaceRepository();
        FakeEnrichmentJobRepository jobRepo = new FakeEnrichmentJobRepository();
        AtomicInteger backgroundLaunches = new AtomicInteger();

        UUID legacyJobId = UUID.randomUUID();
        jobRepo.addPendingLookup(
                new PendingCoordinateJob(
                        legacyJobId,
                        UUID.randomUUID(),
                        "personal",
                        ImportRecordKind.SAVED_CSV,
                        "Wawel Legacy S2",
                        "https://www.google.com/maps/place/Wawel/data=!4m2!3m1!1s0x47165b120455da67:0x999004",
                        null,
                        null,
                        "{\"listName\":\"Want to go\"}"));

        RunStartupIngestionSweep sweep =
                createSweep(tempDir, placeRepo, jobRepo, true, backgroundLaunches);

        sweep.execute();

        assertThat(jobRepo.countPendingCoordinateLookups(USER_ID)).isZero();
        assertThat(jobRepo.enqueuedEnrichmentPlaceIds()).hasSize(1);
        assertThat(backgroundLaunches.get()).isEqualTo(1);
    }

    @Test
    void skipsMaterializationAndBackgroundDrainWhenEmptyOrDisabled(@TempDir Path tempDir) {
        FakeAtlasPlaceRepository placeRepo = new FakeAtlasPlaceRepository();
        FakeEnrichmentJobRepository jobRepo = new FakeEnrichmentJobRepository();
        AtomicInteger backgroundLaunches = new AtomicInteger();

        RunStartupIngestionSweep sweep =
                createSweep(tempDir, placeRepo, jobRepo, false, backgroundLaunches);
        sweep.execute();
        assertThat(backgroundLaunches.get()).isZero();

        UUID legacyJobId = UUID.randomUUID();
        jobRepo.addPendingLookup(
                new PendingCoordinateJob(
                        legacyJobId,
                        UUID.randomUUID(),
                        "personal",
                        ImportRecordKind.SAVED_CSV,
                        "47°55'12.3\"N 106°54'19.8\"E",
                        null,
                        null,
                        null,
                        "{\"listName\":\"Want to go\"}"));

        sweep.execute();
        assertThat(jobRepo.countPendingCoordinateLookups(USER_ID)).isZero();
        assertThat(backgroundLaunches.get()).isZero();
    }

    private static RunStartupIngestionSweep createSweep(
            Path tempDir,
            FakeAtlasPlaceRepository placeRepo,
            FakeEnrichmentJobRepository jobRepo,
            boolean enrichmentEnabled,
            AtomicInteger backgroundLaunches) {
        CurrentUserProvider userProvider = () -> USER_ID;
        AtlasFacade atlasFacade =
                AtlasFacadeTestSupport.create(
                        placeRepo,
                        new NoOpBoundaryRepo(),
                        new NoOpStatsRepo(),
                        tempDir,
                        userProvider);
        MaterializePendingCoordinateJobs materialize =
                new MaterializePendingCoordinateJobs(
                        jobRepo,
                        atlasFacade,
                        userProvider,
                        TransactionOperations.withoutTransaction());
        RunEnrichmentBatch runEnrichmentBatch =
                new RunEnrichmentBatch(
                        properties(enrichmentEnabled, 10, 2),
                        jobRepo,
                        new NoOpCatalogPort(),
                        atlasFacade,
                        userProvider,
                        TransactionOperations.withoutTransaction(),
                        task -> {
                            backgroundLaunches.incrementAndGet();
                            task.run();
                        },
                        Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"), ZoneOffset.UTC));
        return new RunStartupIngestionSweep(jobRepo, materialize, runEnrichmentBatch, userProvider);
    }

    private static final class NoOpCatalogPort implements OpenPoiCatalogPort {
        @Override
        public Map<PlaceId, EnrichmentLookupOutcome> enrichAnchoredBatch(
                List<AnchoredEnrichmentTarget> targets) {
            return Map.of();
        }

        @Override
        public EnrichmentLookupOutcome resolveUnanchored(UnanchoredLookupTarget target) {
            return new EnrichmentLookupOutcome.NoMatch("noop");
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
