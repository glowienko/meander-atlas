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
import com.meanderatlas.ingestion.OfflineMaterializationSummaryView;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionOperations;

class MaterializePendingCoordinateJobsTest {

    private static final UserId USER_ID =
            new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    void materializesDmsS2CidAndPagesBeyondBatchSizeIdempotently(@TempDir Path tempDir) {
        FakeAtlasPlaceRepository placeRepo = new FakeAtlasPlaceRepository();
        FakeEnrichmentJobRepository jobRepo = new FakeEnrichmentJobRepository();

        PlaceId existingPlaceId =
                placeRepo.insertImportedPlace(
                        USER_ID,
                        "Existing Wawel",
                        new GeoPoint(50.0540, 19.9354),
                        CoordSource.TAKEOUT,
                        null,
                        777001L,
                        PlaceCategory.UNCATEGORIZED,
                        null,
                        CategorySource.HEURISTIC,
                        DerivedRole.BACKLOG,
                        "PL",
                        "Krakow");

        UUID cidJobId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID dmsJobId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UUID s2JobId = UUID.fromString("00000000-0000-0000-0000-000000000103");
        UUID reviewCidJobId = UUID.fromString("00000000-0000-0000-0000-000000000104");
        UUID unresolvableJobId = UUID.fromString("00000000-0000-0000-0000-000000000105");

        jobRepo.addPendingLookup(
                new PendingCoordinateJob(
                        cidJobId,
                        UUID.randomUUID(),
                        "personal",
                        ImportRecordKind.SAVED_CSV,
                        "Existing Wawel",
                        "https://www.google.com/maps/place/Wawel/data=!4m2!3m1!1s0x10000001:0x"
                                + Long.toHexString(777001L),
                        777001L,
                        null,
                        "{\"listName\":\"Starred Places\",\"listKind\":\"STARRED\",\"note\":\"Great castle\"}"));
        jobRepo.addPendingLookup(
                new PendingCoordinateJob(
                        dmsJobId,
                        UUID.randomUUID(),
                        "personal",
                        ImportRecordKind.SAVED_CSV,
                        "47°55'12.3\"N 106°54'19.8\"E",
                        "https://www.google.com/maps/search/?api=1&query=47.92,106.90",
                        null,
                        null,
                        "{\"listName\":\"Want to go\",\"listKind\":\"WANT_TO_GO\",\"comment\":\"Yurt camp\"}"));
        jobRepo.addPendingLookup(
                new PendingCoordinateJob(
                        s2JobId,
                        UUID.randomUUID(),
                        "personal",
                        ImportRecordKind.SAVED_CSV,
                        "Miradouro da Senhora",
                        "https://www.google.com/maps/place/Lima/data=!4m2!3m1!1s0x9105c8cdc0000000:0x888001",
                        null,
                        null,
                        "{\"listName\":\"Custom Lisbon\"}"));
        jobRepo.addPendingLookup(
                new PendingCoordinateJob(
                        reviewCidJobId,
                        UUID.randomUUID(),
                        "personal",
                        ImportRecordKind.REVIEW_GEOJSON,
                        "Existing Wawel",
                        "http://maps.google.com/?cid=777001",
                        777001L,
                        Instant.parse("2026-07-01T10:00:00Z"),
                        "{\"name\":\"Existing Wawel\",\"reviewText\":\"Visited again\"}"));
        jobRepo.addPendingLookup(
                new PendingCoordinateJob(
                        unresolvableJobId,
                        UUID.randomUUID(),
                        "personal",
                        ImportRecordKind.SAVED_CSV,
                        "Unknown Stub Spot",
                        "https://www.google.com/maps/place/Stub/data=!4m2!3m1!1s0x10000002:0x0",
                        null,
                        null,
                        "{\"listName\":\"Want to go\"}"));

        IntStream.range(0, 205)
                .forEach(index ->
                        jobRepo.addPendingLookup(
                                new PendingCoordinateJob(
                                        UUID.fromString(
                                                String.format(
                                                        "00000000-0000-0000-0001-%012d", index)),
                                        UUID.randomUUID(),
                                        "personal",
                                        ImportRecordKind.SAVED_CSV,
                                        "Stub " + index,
                                        "https://www.google.com/maps/search/?api=1&query=0,0",
                                        null,
                                        null,
                                        "{}")));

        MaterializePendingCoordinateJobs useCase = createUseCase(tempDir, placeRepo, jobRepo);

        OfflineMaterializationSummaryView first = useCase.execute();

        assertThat(first.materializedCount()).isEqualTo(4);
        assertThat(first.exactFromTitleCount()).isEqualTo(1);
        assertThat(first.approxFromS2Count()).isEqualTo(1);
        assertThat(first.mergedByCidCount()).isEqualTo(2);
        assertThat(first.remainingLookupCount()).isEqualTo(206);
        assertThat(first.enqueuedEnrichmentCount()).isEqualTo(3);

        assertThat(jobRepo.isCompleted(cidJobId)).isTrue();
        assertThat(jobRepo.completedPlaceId(cidJobId)).isEqualTo(existingPlaceId);
        assertThat(jobRepo.isCompleted(reviewCidJobId)).isTrue();
        assertThat(jobRepo.isCompleted(dmsJobId)).isTrue();
        assertThat(jobRepo.isCompleted(s2JobId)).isTrue();
        assertThat(jobRepo.isCompleted(unresolvableJobId)).isFalse();

        PlaceId dmsPlaceId = jobRepo.completedPlaceId(dmsJobId);
        PlaceId s2PlaceId = jobRepo.completedPlaceId(s2JobId);
        assertThat(dmsPlaceId).isNotNull();
        assertThat(s2PlaceId).isNotNull();
        assertThat(placeRepo.place(dmsPlaceId).coordSource).isEqualTo(CoordSource.TAKEOUT);
        assertThat(placeRepo.place(s2PlaceId).coordSource).isEqualTo(CoordSource.TAKEOUT_S2_APPROX);
        assertThat(placeRepo.place(s2PlaceId).category).isEqualTo(PlaceCategory.VIEWPOINT);
        assertThat(jobRepo.enqueuedEnrichmentPlaceIds())
                .containsExactlyInAnyOrder(existingPlaceId, dmsPlaceId, s2PlaceId);

        OfflineMaterializationSummaryView second = useCase.execute();
        assertThat(second.materializedCount()).isZero();
        assertThat(second.enqueuedEnrichmentCount()).isZero();
        assertThat(second.remainingLookupCount()).isEqualTo(206);
    }

    @Test
    void upgradesExistingS2ApproxCidPlaceWhenMatchingCsvJobHasDmsTitleCoordinates(
            @TempDir Path tempDir) {
        FakeAtlasPlaceRepository placeRepo = new FakeAtlasPlaceRepository();
        FakeEnrichmentJobRepository jobRepo = new FakeEnrichmentJobRepository();

        PlaceId s2CidPlaceId =
                placeRepo.insertImportedPlace(
                        USER_ID,
                        "Approx CID Spot",
                        new GeoPoint(47.90, 106.90),
                        CoordSource.TAKEOUT_S2_APPROX,
                        null,
                        2002L,
                        PlaceCategory.UNCATEGORIZED,
                        null,
                        CategorySource.HEURISTIC,
                        DerivedRole.BACKLOG,
                        null,
                        null);

        UUID cidDmsJobId = UUID.fromString("00000000-0000-0000-0000-000000000202");
        jobRepo.addPendingLookup(
                new PendingCoordinateJob(
                        cidDmsJobId,
                        UUID.randomUUID(),
                        "personal",
                        ImportRecordKind.SAVED_CSV,
                        "47°55'12.3\"N 106°54'19.8\"E",
                        "https://www.google.com/maps/place/Spot/data=!4m2!3m1!1s0x1:0x7d2",
                        2002L,
                        null,
                        "{\"listName\":\"Want to go\",\"listKind\":\"WANT_TO_GO\"}"));

        MaterializePendingCoordinateJobs useCase = createUseCase(tempDir, placeRepo, jobRepo);
        OfflineMaterializationSummaryView summary = useCase.execute();

        assertThat(summary.materializedCount()).isEqualTo(1);
        assertThat(summary.mergedByCidCount()).isEqualTo(1);
        assertThat(jobRepo.isCompleted(cidDmsJobId)).isTrue();
        assertThat(jobRepo.completedPlaceId(cidDmsJobId)).isEqualTo(s2CidPlaceId);
        assertThat(placeRepo.place(s2CidPlaceId).coordSource).isEqualTo(CoordSource.TAKEOUT);
        assertThat(placeRepo.place(s2CidPlaceId).coordinates.latitude())
                .isCloseTo(47.9200833, org.assertj.core.api.Assertions.within(1e-6));
        assertThat(placeRepo.place(s2CidPlaceId).coordinates.longitude())
                .isCloseTo(106.9055, org.assertj.core.api.Assertions.within(1e-6));
    }

    private static MaterializePendingCoordinateJobs createUseCase(
            Path tempDir, FakeAtlasPlaceRepository placeRepo, FakeEnrichmentJobRepository jobRepo) {
        CurrentUserProvider userProvider = () -> USER_ID;
        AtlasFacade atlasFacade =
                AtlasFacadeTestSupport.create(
                        placeRepo,
                        new NoOpRegionBoundaryRepository(),
                        new NoOpAtlasStatsRepository(),
                        tempDir,
                        userProvider);
        return new MaterializePendingCoordinateJobs(
                jobRepo, atlasFacade, userProvider, TransactionOperations.withoutTransaction());
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
