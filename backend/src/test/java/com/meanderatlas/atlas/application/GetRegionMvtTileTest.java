package com.meanderatlas.atlas.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meanderatlas.atlas.RegionScope;
import com.meanderatlas.atlas.domain.RegionLevel;
import com.meanderatlas.atlas.domain.boundaries.BoundaryFeature;
import com.meanderatlas.atlas.domain.boundaries.RegionBoundaryRepository;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class GetRegionMvtTileTest {

    @Test
    void validatesTileCoordinatesAndNormalizesScopeAndCountryCode() {
        CapturingBoundaryRepository repo = new CapturingBoundaryRepository();
        GetRegionMvtTile useCase = new GetRegionMvtTile(repo);

        byte[] world = useCase.execute(RegionScope.COUNTRY, 0, 0, 0, "   ");
        assertThat(world).containsExactly(1, 2);
        assertThat(repo.lastLevel).isEqualTo(RegionLevel.COUNTRY);
        assertThat(repo.lastCountryCode).isNull();

        useCase.execute(RegionScope.ADMIN1, 4, 8, 5, " fr ");
        assertThat(repo.lastLevel).isEqualTo(RegionLevel.ADMIN1);
        assertThat(repo.lastCountryCode).isEqualTo("FR");

        useCase.execute(RegionScope.ADMIN1, 14, 16383, 16383, null);
        assertThat(repo.lastZ).isEqualTo(14);
        assertThat(repo.lastCountryCode).isNull();

        assertThatThrownBy(() -> useCase.execute(RegionScope.COUNTRY, -1, 0, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(RegionScope.COUNTRY, 15, 0, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(RegionScope.COUNTRY, 0, 1, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(RegionScope.COUNTRY, 1, -1, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(RegionScope.COUNTRY, 1, 0, 2, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class CapturingBoundaryRepository implements RegionBoundaryRepository {

        private @Nullable RegionLevel lastLevel;
        private int lastZ;
        private @Nullable String lastCountryCode;

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
            this.lastLevel = level;
            this.lastZ = z;
            this.lastCountryCode = countryCode;
            return new byte[] {1, 2};
        }
    }
}
