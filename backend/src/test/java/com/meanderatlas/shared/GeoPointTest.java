package com.meanderatlas.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class GeoPointTest {

    private static final double OUT_OF_RANGE_LATITUDE = 90.1;
    private static final double OUT_OF_RANGE_LONGITUDE = -180.1;
    private static final double ORIGIN_COORDINATE = 0.0;
    private static final double VALID_COORDINATE = 10.0;
    private static final String SAMPLE_USER_ID = "00000000-0000-0000-0000-000000000001";

    @Test
    void acceptsBoundaryCoordinates() {
        GeoPoint northEast = new GeoPoint(GeoPoint.MAX_LATITUDE, GeoPoint.MAX_LONGITUDE);
        GeoPoint southWest = new GeoPoint(GeoPoint.MIN_LATITUDE, GeoPoint.MIN_LONGITUDE);

        assertThat(northEast.latitude()).isEqualTo(GeoPoint.MAX_LATITUDE);
        assertThat(southWest.longitude()).isEqualTo(GeoPoint.MIN_LONGITUDE);
    }

    @Test
    void rejectsOutOfRangeOrNonFiniteCoordinates() {
        assertThatThrownBy(() -> new GeoPoint(OUT_OF_RANGE_LATITUDE, ORIGIN_COORDINATE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(ORIGIN_COORDINATE, OUT_OF_RANGE_LONGITUDE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(Double.NaN, VALID_COORDINATE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(VALID_COORDINATE, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesTypedIdentifiers() {
        UUID raw = UUID.fromString(SAMPLE_USER_ID);
        UserId userId = UserId.fromString(raw.toString());
        PlaceId placeId = PlaceId.random();

        assertThat(userId.value()).isEqualTo(raw);
        assertThat(placeId.value()).isNotNull();
    }
}
