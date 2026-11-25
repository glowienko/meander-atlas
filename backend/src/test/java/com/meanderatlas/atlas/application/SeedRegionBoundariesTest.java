package com.meanderatlas.atlas.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meanderatlas.atlas.BoundarySeedResultView;
import com.meanderatlas.atlas.domain.RegionLevel;
import com.meanderatlas.atlas.domain.boundaries.BoundaryFeature;
import com.meanderatlas.atlas.domain.boundaries.BoundaryTestData;
import com.meanderatlas.atlas.domain.boundaries.RegionBoundaryRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionOperations;

class SeedRegionBoundariesTest {

    @Test
    void skipsWhenFilesDoNotExist(@TempDir Path tempDir) {
        FakeRegionBoundaryRepository fakeRepo = new FakeRegionBoundaryRepository();
        SeedRegionBoundaries useCase =
                new SeedRegionBoundaries(
                        fakeRepo, tempDir.toString(), TransactionOperations.withoutTransaction());

        BoundarySeedResultView result = useCase.execute(false);

        assertThat(result.skipped()).isTrue();
        assertThat(result.countriesUpserted()).isZero();
        assertThat(result.admin1Upserted()).isZero();
        assertThat(fakeRepo.stored).isEmpty();
    }

    @Test
    void seedsIdempotentlyAndReSeedsWhenForced(@TempDir Path tempDir) throws IOException {
        BoundaryTestData.writePolandOnly(tempDir);
        FakeRegionBoundaryRepository fakeRepo = new FakeRegionBoundaryRepository();
        fakeRepo.placesTaggedToReturn = 3;
        SeedRegionBoundaries useCase =
                new SeedRegionBoundaries(
                        fakeRepo, tempDir.toString(), TransactionOperations.withoutTransaction());

        BoundarySeedResultView first = useCase.execute(false);
        assertThat(first.skipped()).isFalse();
        assertThat(first.countriesUpserted()).isEqualTo(1);
        assertThat(first.admin1Upserted()).isEqualTo(1);
        assertThat(first.placesTagged()).isEqualTo(3);

        BoundarySeedResultView second = useCase.execute(false);
        assertThat(second.skipped()).isTrue();
        assertThat(second.countriesUpserted()).isZero();
        assertThat(second.admin1Upserted()).isZero();

        BoundarySeedResultView forced = useCase.execute(true);
        assertThat(forced.skipped()).isFalse();
        assertThat(forced.countriesUpserted()).isEqualTo(1);
        assertThat(forced.admin1Upserted()).isEqualTo(1);
    }

    @Test
    void seedsMissingAdmin1WhenCountriesPresentAndSupportsAdmin1OnlyDir(@TempDir Path tempDir)
            throws IOException {
        Path bothDir = tempDir.resolve("both");
        BoundaryTestData.writePolandOnly(bothDir);
        FakeRegionBoundaryRepository fakeRepo = new FakeRegionBoundaryRepository();
        fakeRepo.upsertBoundaries(
                List.of(
                        new BoundaryFeature(
                                RegionLevel.COUNTRY,
                                "PL",
                                "PL",
                                "Poland",
                                "{\"type\":\"Polygon\",\"coordinates\":[]}")),
                0.02);

        BoundarySeedResultView partial =
                new SeedRegionBoundaries(
                                fakeRepo,
                                bothDir.toString(),
                                TransactionOperations.withoutTransaction())
                        .execute(false);
        assertThat(partial.skipped()).isFalse();
        assertThat(partial.countriesUpserted()).isZero();
        assertThat(partial.admin1Upserted()).isEqualTo(1);

        Path admin1OnlyDir = tempDir.resolve("only-admin1");
        BoundaryTestData.writeGeoJson(
                admin1OnlyDir,
                "ne_10m_admin_1_states_provinces.geojson",
                BoundaryTestData.singleMalopolskieAdmin1GeoJson());
        FakeRegionBoundaryRepository admin1OnlyRepo = new FakeRegionBoundaryRepository();
        BoundarySeedResultView admin1OnlyResult =
                new SeedRegionBoundaries(
                                admin1OnlyRepo,
                                admin1OnlyDir.toString(),
                                TransactionOperations.withoutTransaction())
                        .execute(false);
        assertThat(admin1OnlyResult.countriesUpserted()).isZero();
        assertThat(admin1OnlyResult.admin1Upserted()).isEqualTo(1);

        Path invalidPathDir = tempDir.resolve("broken-path");
        BoundaryTestData.writeGeoJson(
                invalidPathDir, "ne_10m_admin_0_countries.geojson", "{not-json");
        SeedRegionBoundaries brokenUseCase =
                new SeedRegionBoundaries(
                        new FakeRegionBoundaryRepository(),
                        invalidPathDir.toString(),
                        TransactionOperations.withoutTransaction());
        assertThatThrownBy(() -> brokenUseCase.execute(false))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(IllegalArgumentException.class);
    }

    private static final class FakeRegionBoundaryRepository implements RegionBoundaryRepository {

        private final List<BoundaryFeature> stored = new ArrayList<>();
        private int placesTaggedToReturn;

        @Override
        public int upsertBoundaries(List<BoundaryFeature> features, double simplifyTolerance) {
            stored.removeIf(existing ->
                    features.stream()
                            .anyMatch(incoming ->
                                    incoming.level() == existing.level()
                                            && incoming.code().equals(existing.code())));
            stored.addAll(features);
            return features.size();
        }

        @Override
        public long countByLevel(RegionLevel level) {
            return stored.stream().filter(feature -> feature.level() == level).count();
        }

        @Override
        public int assignRegionsToUntaggedPlaces() {
            return placesTaggedToReturn;
        }

        @Override
        public byte[] fetchRegionMvtTile(
                RegionLevel level, int z, int x, int y, @Nullable String countryCode) {
            return new byte[0];
        }
    }
}
