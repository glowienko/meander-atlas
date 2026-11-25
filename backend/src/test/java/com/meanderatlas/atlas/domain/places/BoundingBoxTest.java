package com.meanderatlas.atlas.domain.places;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BoundingBoxTest {

    @Test
    void acceptsValidGeographicBounds() {
        BoundingBox box = new BoundingBox(19.8, 50.0, 20.1, 50.1);

        assertThat(box.minLng()).isEqualTo(19.8);
        assertThat(box.maxLat()).isEqualTo(50.1);
    }

    @Test
    void rejectsInvertedOrOutOfRangeCoordinates() {
        assertThatThrownBy(() -> new BoundingBox(20.1, 50.0, 19.8, 50.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoundingBox(19.8, 50.2, 20.1, 50.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoundingBox(-181.0, 0.0, 10.0, 10.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoundingBox(0.0, -91.0, 10.0, 10.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
