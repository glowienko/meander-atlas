package com.meanderatlas.ingestion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.meanderatlas.shared.GeoPoint;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GoogleS2CellDecoderTest {

    private static final double COORD_TOLERANCE = 1e-6;

    @Test
    void decodesRealFtidUrlsAcrossAllSixS2FacesAndLevels() {
        assertUrlDecodesTo(
                "https://www.google.com/maps/place/Wawel/data=!4m2!3m1!1s0x47165b120455da67:0x20000001",
                50.06098312,
                19.93841589);
        assertUrlDecodesTo(
                "https://www.google.com/maps/place/Lima/data=!4m2!3m1!1s0x9105c8cdc0000000:0x1234",
                -12.04553920,
                -77.04349337);
        assertUrlDecodesTo(
                "https://www.google.com/maps/place/Ushuaia/data=!4m2!3m1!1s0xbc4c22dec0000000%3A0x5678",
                -54.80191719, -68.30181253);
        assertUrlDecodesTo(
                "https://www.google.com/maps?ftid=0x05d5555540000000:0x1&hl=en",
                -9.81809905,
                -22.61837508);
        assertUrlDecodesTo(
                "https://www.google.com/maps/place/Face1/data=!4m2!3m1!1s0X2480000040000000:0X2",
                -9.81809905,
                67.38162492);
        assertUrlDecodesTo(
                "https://www.google.com/maps/place/Face2/data=!4m2!3m1!1s0x45d5555540000000:0x3",
                65.44564719,
                24.22640747);
        assertUrlDecodesTo(
                "https://www.google.com/maps/place/Face3/data=!4m2!3m1!1s0x6480000040000000:0x4",
                22.26919629,
                169.38175233);
        assertUrlDecodesTo(
                "https://www.google.com/maps/place/Face4/data=!4m2!3m1!1s0x85d5555540000000:0x5",
                22.26919629,
                -100.61824767);
        assertUrlDecodesTo(
                "https://www.google.com/maps/place/Face5/data=!4m2!3m1!1s0xa480000040000000:0x6",
                -65.44564719,
                -114.22640747);
    }

    @Test
    void rejectsSyntheticLowBitStubsInvalidFacesInvalidSentinelAndMalformedInputs() {
        assertThat(GoogleS2CellDecoder.decodeFromUrl(null)).isEmpty();
        assertThat(GoogleS2CellDecoder.decodeFromUrl("   ")).isEmpty();
        assertThat(GoogleS2CellDecoder.decodeFromUrl("http://maps.google.com/?cid=536870913"))
                .isEmpty();
        assertThat(
                        GoogleS2CellDecoder.decodeFromUrl(
                                "https://www.google.com/maps/place/Wawel/data=!4m2!3m1!1s0x10000001:0x20000001"))
                .isEmpty();
        assertThat(
                        GoogleS2CellDecoder.decodeFromUrl(
                                "https://www.google.com/maps/place/Stub/data=!4m2!3m1!1s0x10000021:0x20000021"))
                .isEmpty();
        assertThat(
                        GoogleS2CellDecoder.decodeFromUrl(
                                "https://www.google.com/maps/place/Zero/data=!4m2!3m1!1s0x0:0x1"))
                .isEmpty();
        assertThat(
                        GoogleS2CellDecoder.decodeFromUrl(
                                "https://www.google.com/maps/place/Overflow/data=!4m2!3m1!1s0x10000000000000000:0x1"))
                .isEmpty();
        assertThat(GoogleS2CellDecoder.decodeCellId(0L)).isEmpty();
        assertThat(GoogleS2CellDecoder.decodeCellId(0x0000000010000001L)).isEmpty();
        assertThat(GoogleS2CellDecoder.decodeCellId(0xc000000000000000L)).isEmpty();
        assertThat(GoogleS2CellDecoder.decodeCellId(0xe000000000000000L)).isEmpty();
        assertThat(GoogleS2CellDecoder.decodeCellId(0x47165b6d20000000L)).isEmpty();
        assertThat(GoogleS2CellDecoder.decodeCellId(0x2000000000000000L)).isEmpty();
    }

    private static void assertUrlDecodesTo(String url, double expectedLat, double expectedLng) {
        Optional<GeoPoint> decoded = GoogleS2CellDecoder.decodeFromUrl(url);
        assertThat(decoded).as(url).isPresent();
        assertThat(decoded.get().latitude()).isCloseTo(expectedLat, within(COORD_TOLERANCE));
        assertThat(decoded.get().longitude()).isCloseTo(expectedLng, within(COORD_TOLERANCE));
    }
}
