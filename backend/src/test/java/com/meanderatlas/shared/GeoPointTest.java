package com.meanderatlas.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class GeoPointTest {

    @Test
    void acceptsBoundaryCoordinates() {
        GeoPoint northPole = new GeoPoint(90.0, 180.0);
        GeoPoint southPole = new GeoPoint(-90.0, -180.0);

        assertThat(northPole.latitude()).isEqualTo(90.0);
        assertThat(southPole.longitude()).isEqualTo(-180.0);
    }

    @Test
    void rejectsOutOfRangeOrNonFiniteCoordinates() {
        assertThatThrownBy(() -> new GeoPoint(90.1, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(0.0, -180.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(Double.NaN, 10.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(10.0, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesTypedIdentifiers() {
        UUID raw = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UserId userId = UserId.fromString(raw.toString());
        PlaceId placeId = PlaceId.random();

        assertThat(userId.value()).isEqualTo(raw);
        assertThat(placeId.value()).isNotNull();
    }
}
