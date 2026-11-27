package com.meanderatlas.atlas.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meanderatlas.atlas.AtlasFacade;
import com.meanderatlas.atlas.PlaceEnrichmentCommand;
import com.meanderatlas.atlas.PlaceEnrichmentResultView;
import com.meanderatlas.atlas.PlaceEnrichmentTargetView;
import com.meanderatlas.atlas.domain.RegionLevel;
import com.meanderatlas.atlas.domain.boundaries.BoundaryFeature;
import com.meanderatlas.atlas.domain.boundaries.RegionBoundaryRepository;
import com.meanderatlas.atlas.domain.stats.AtlasStats;
import com.meanderatlas.atlas.domain.stats.AtlasStatsRepository;
import com.meanderatlas.atlas.domain.stats.RegionSummary;
import com.meanderatlas.atlas.domain.stats.RegionSummaryFilter;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplyPlaceEnrichmentTest {

    private static final UserId USER_ID =
            new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    void upgradesApproximateCoordsAndHeuristicCategoryWhileRecomputingDerivedRole(
            @TempDir Path tempDir) {
        FakeAtlasPlaceRepository placeRepo = new FakeAtlasPlaceRepository();
        AtlasFacade facade = createFacade(tempDir, placeRepo);

        PlaceId approxId =
                placeRepo.insertImportedPlace(
                        USER_ID,
                        "Hotel Copernicus",
                        new GeoPoint(50.0500, 19.9300),
                        CoordSource.TAKEOUT_S2_APPROX,
                        null,
                        null,
                        PlaceCategory.UNCATEGORIZED,
                        null,
                        CategorySource.HEURISTIC,
                        DerivedRole.MUST_SEE,
                        "PL",
                        null);
        placeRepo.upsertMembership(
                USER_ID,
                approxId,
                "personal",
                "Starred Places",
                ListKind.STARRED,
                null,
                null,
                null,
                null);

        List<PlaceEnrichmentTargetView> targets = facade.findEnrichmentTargets(List.of(approxId));
        assertThat(targets).hasSize(1);
        assertThat(targets.getFirst().coordSource()).isEqualTo(CoordSource.TAKEOUT_S2_APPROX);

        PlaceEnrichmentResultView result =
                facade.applyPlaceEnrichment(
                        new PlaceEnrichmentCommand(
                                approxId,
                                new GeoPoint(50.0558, 19.9371),
                                "way/4242",
                                PlaceCategory.HOTEL,
                                "hotel",
                                "Kraków",
                                "https://upload.wikimedia.org/wikipedia/commons/thumb/c/Copernicus.jpg",
                                "{\"raw\":\"24/7\"}",
                                "Hotel Copernicus Official"));

        assertThat(result.coordsUpgraded()).isTrue();
        assertThat(result.categoryUpgraded()).isTrue();
        assertThat(result.photoAdded()).isTrue();

        FakeAtlasPlaceRepository.MutablePlaceRecord stored = placeRepo.place(approxId);
        assertThat(stored.coordSource).isEqualTo(CoordSource.OPEN_CATALOG);
        assertThat(stored.coordinates).isEqualTo(new GeoPoint(50.0558, 19.9371));
        assertThat(stored.osmId).isEqualTo("way/4242");
        assertThat(stored.category).isEqualTo(PlaceCategory.HOTEL);
        assertThat(stored.subcategory).isEqualTo("hotel");
        assertThat(stored.categorySource).isEqualTo(CategorySource.OPEN_CATALOG);
        assertThat(stored.derivedRole).isEqualTo(DerivedRole.PREFERRED_HOTEL);
        assertThat(stored.cityName).isEqualTo("Kraków");
        assertThat(stored.photoUrl)
                .isEqualTo("https://upload.wikimedia.org/wikipedia/commons/thumb/c/Copernicus.jpg");
        assertThat(stored.openingHoursJson).isEqualTo("{\"raw\":\"24/7\"}");
    }

    @Test
    void preservesExactCoordinatesManualCategoryExistingPhotoAndExistingCity(
            @TempDir Path tempDir) {
        FakeAtlasPlaceRepository placeRepo = new FakeAtlasPlaceRepository();
        AtlasFacade facade = createFacade(tempDir, placeRepo);

        PlaceId exactManualId =
                placeRepo.insertImportedPlace(
                        USER_ID,
                        "Cafe Camelot",
                        new GeoPoint(50.0630, 19.9390),
                        CoordSource.TAKEOUT,
                        null,
                        null,
                        PlaceCategory.MUSEUM,
                        "custom",
                        CategorySource.MANUAL,
                        DerivedRole.BACKLOG,
                        "PL",
                        "Krakow");
        placeRepo.place(exactManualId).photoUrl =
                "https://upload.wikimedia.org/wikipedia/commons/existing.jpg";

        PlaceEnrichmentResultView result =
                facade.applyPlaceEnrichment(
                        new PlaceEnrichmentCommand(
                                exactManualId,
                                new GeoPoint(50.0635, 19.9395),
                                "node/777",
                                PlaceCategory.CAFE_BAR,
                                "cafe",
                                "Other City",
                                "https://upload.wikimedia.org/wikipedia/commons/new.jpg",
                                null,
                                "Different Name"));

        assertThat(result.coordsUpgraded()).isFalse();
        assertThat(result.categoryUpgraded()).isFalse();
        assertThat(result.photoAdded()).isFalse();

        FakeAtlasPlaceRepository.MutablePlaceRecord stored = placeRepo.place(exactManualId);
        assertThat(stored.coordSource).isEqualTo(CoordSource.TAKEOUT);
        assertThat(stored.coordinates).isEqualTo(new GeoPoint(50.0630, 19.9390));
        assertThat(stored.category).isEqualTo(PlaceCategory.MUSEUM);
        assertThat(stored.categorySource).isEqualTo(CategorySource.MANUAL);
        assertThat(stored.cityName).isEqualTo("Krakow");
        assertThat(stored.photoUrl)
                .isEqualTo("https://upload.wikimedia.org/wikipedia/commons/existing.jpg");
        assertThat(stored.osmId).isEqualTo("node/777");
        assertThat(stored.canonicalName).isEqualTo("Cafe Camelot");
    }

    @Test
    void upgradesCoordinateOnlyCanonicalNameWithoutOverwritingNormalNames(@TempDir Path tempDir) {
        FakeAtlasPlaceRepository placeRepo = new FakeAtlasPlaceRepository();
        AtlasFacade facade = createFacade(tempDir, placeRepo);

        PlaceId decimalId =
                placeRepo.insertImportedPlace(
                        USER_ID,
                        "52.226346,5.177603",
                        new GeoPoint(52.226346, 5.177603),
                        CoordSource.TAKEOUT,
                        null,
                        null,
                        PlaceCategory.UNCATEGORIZED,
                        null,
                        CategorySource.HEURISTIC,
                        DerivedRole.BACKLOG,
                        "NL",
                        null);
        PlaceId dmsId =
                placeRepo.insertImportedPlace(
                        USER_ID,
                        "47°55'12.3\"N 106°54'19.8\"E",
                        new GeoPoint(47.920083, 106.9055),
                        CoordSource.TAKEOUT,
                        null,
                        null,
                        PlaceCategory.UNCATEGORIZED,
                        null,
                        CategorySource.HEURISTIC,
                        DerivedRole.BACKLOG,
                        "MN",
                        null);

        facade.applyPlaceEnrichment(
                new PlaceEnrichmentCommand(
                        decimalId,
                        null,
                        "node/100",
                        PlaceCategory.LANDMARK,
                        "square",
                        "Hilversum",
                        null,
                        null,
                        "Marktplein Hilversum"));
        facade.applyPlaceEnrichment(
                new PlaceEnrichmentCommand(
                        dmsId,
                        null,
                        "way/200",
                        null,
                        null,
                        "Ulaanbaatar",
                        null,
                        null,
                        "Sukhbaatar Square"));

        assertThat(placeRepo.place(decimalId).canonicalName).isEqualTo("Marktplein Hilversum");
        assertThat(placeRepo.place(dmsId).canonicalName).isEqualTo("Sukhbaatar Square");
    }

    @Test
    void throwsWhenPlaceDoesNotExist(@TempDir Path tempDir) {
        FakeAtlasPlaceRepository placeRepo = new FakeAtlasPlaceRepository();
        AtlasFacade facade = createFacade(tempDir, placeRepo);
        assertThat(facade.findEnrichmentTargets(List.of())).isEmpty();
        assertThatThrownBy(() ->
                        facade.applyPlaceEnrichment(
                                new PlaceEnrichmentCommand(
                                        PlaceId.random(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)))
                .isInstanceOf(NoSuchElementException.class);
    }

    private static AtlasFacade createFacade(Path tempDir, FakeAtlasPlaceRepository placeRepo) {
        CurrentUserProvider userProvider = () -> USER_ID;
        return AtlasFacadeTestSupport.create(
                placeRepo, new NoOpBoundaryRepo(), new NoOpStatsRepo(), tempDir, userProvider);
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
