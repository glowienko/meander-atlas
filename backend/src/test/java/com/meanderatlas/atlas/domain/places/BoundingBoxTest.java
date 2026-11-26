package com.meanderatlas.atlas.domain.places;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BoundingBoxTest {

    @Test
    void acceptsValidAndAntimeridianGeographicBounds() {
        BoundingBox box = new BoundingBox(19.8, 50.0, 20.1, 50.1);
        assertThat(box.minLng()).isEqualTo(19.8);
        assertThat(box.maxLat()).isEqualTo(50.1);
        assertThat(box.crossesAntimeridian()).isFalse();

        BoundingBox antimeridian = new BoundingBox(170.0, -20.0, -170.0, 20.0);
        assertThat(antimeridian.minLng()).isEqualTo(170.0);
        assertThat(antimeridian.maxLng()).isEqualTo(-170.0);
        assertThat(antimeridian.crossesAntimeridian()).isTrue();
    }

    @Test
    void normalizesFullWorldAndUnwrappedMapLibreLongitudes() {
        BoundingBox fullWorld = new BoundingBox(-240.0, -85.0, 240.0, 85.0);
        assertThat(fullWorld.minLng()).isEqualTo(-180.0);
        assertThat(fullWorld.maxLng()).isEqualTo(180.0);
        assertThat(fullWorld.crossesAntimeridian()).isFalse();

        BoundingBox eastWrap = new BoundingBox(170.0, -20.0, 190.0, 20.0);
        assertThat(eastWrap.minLng()).isEqualTo(170.0);
        assertThat(eastWrap.maxLng()).isEqualTo(-170.0);
        assertThat(eastWrap.crossesAntimeridian()).isTrue();

        BoundingBox westWrap = new BoundingBox(-200.0, -20.0, -180.0, 20.0);
        assertThat(westWrap.minLng()).isEqualTo(160.0);
        assertThat(westWrap.maxLng()).isEqualTo(180.0);
        assertThat(westWrap.crossesAntimeridian()).isFalse();
    }

    @Test
    void rejectsNonFiniteOrInvalidLatitudeBounds() {
        assertThatThrownBy(() -> new BoundingBox(19.8, 50.2, 20.1, 50.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoundingBox(0.0, -91.0, 10.0, 10.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoundingBox(0.0, 0.0, 10.0, 91.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoundingBox(Double.NaN, 0.0, 10.0, 10.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
