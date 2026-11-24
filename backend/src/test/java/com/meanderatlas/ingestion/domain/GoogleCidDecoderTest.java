package com.meanderatlas.ingestion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class GoogleCidDecoderTest {

    @Test
    void decodesFtidHexSegmentAndMatchesQueryCid() {
        String csvUrl =
                "https://www.google.com/maps/place/Wawel+Royal+Castle/data=!4m2!3m1!1s0x10000001:0x20000001";
        String geoJsonUrl = "http://maps.google.com/?cid=536870913";

        Optional<GoogleCid> fromFtid = GoogleCidDecoder.decodeFromUrl(csvUrl);
        Optional<GoogleCid> fromQuery = GoogleCidDecoder.decodeFromUrl(geoJsonUrl);

        assertThat(fromFtid).isPresent();
        assertThat(fromFtid.get().decimal()).isEqualTo("536870913");
        assertThat(fromFtid.get().unsignedBits()).isEqualTo(536870913L);
        assertThat(fromQuery).isEqualTo(fromFtid);
    }

    @Test
    void decodesUnsigned64BitHexAndDecimalCidsAboveLongMaxValue() {
        String maxHexUrl =
                "https://www.google.com/maps/place/Example/data=!4m2!3m1!1s0x47165b120455da67:0xffffffffffffffff";
        String maxDecimalUrl = "https://maps.google.com/?cid=18446744073709551615";
        String highBitHexUrl =
                "https://www.google.com/maps/place/Example/data=!4m2!3m1!1s0x1:0x8000000000000000!8m2!3d50.05";
        String highBitDecimalUrl = "https://maps.google.com/?hl=pl&cid=9223372036854775808";
        String mixedHexUrl =
                "https://www.google.com/maps/place/Example/data=!4m2!3m1!1s0x1:0xf123456789abcdef";

        Optional<GoogleCid> maxFromHex = GoogleCidDecoder.decodeFromUrl(maxHexUrl);
        Optional<GoogleCid> maxFromDecimal = GoogleCidDecoder.decodeFromUrl(maxDecimalUrl);
        Optional<GoogleCid> highBitFromHex = GoogleCidDecoder.decodeFromUrl(highBitHexUrl);
        Optional<GoogleCid> highBitFromDecimal = GoogleCidDecoder.decodeFromUrl(highBitDecimalUrl);
        Optional<GoogleCid> mixedFromHex = GoogleCidDecoder.decodeFromUrl(mixedHexUrl);

        assertThat(maxFromHex).isPresent();
        assertThat(maxFromHex.get().decimal()).isEqualTo("18446744073709551615");
        assertThat(maxFromHex.get().unsignedBits()).isEqualTo(-1L);
        assertThat(maxFromDecimal).isEqualTo(maxFromHex);

        assertThat(highBitFromHex).isPresent();
        assertThat(highBitFromHex.get().decimal()).isEqualTo("9223372036854775808");
        assertThat(highBitFromHex.get().unsignedBits()).isEqualTo(Long.MIN_VALUE);
        assertThat(highBitFromDecimal).isEqualTo(highBitFromHex);

        assertThat(mixedFromHex).isPresent();
        assertThat(mixedFromHex.get().decimal()).isEqualTo("17375808098319191535");
    }

    @Test
    void decodesUrlEncodedColonUppercaseHexAndFtidQueryParam() {
        String encodedUrl =
                "https://www.google.com/maps/place/Test/data=!4m2!3m1!1s0X1000000A%3A0X2000000A";
        String ftidParamUrl = "https://www.google.com/maps?ftid=0x1000000a:0x2000000a&hl=en";

        Optional<GoogleCid> encoded = GoogleCidDecoder.decodeFromUrl(encodedUrl);
        Optional<GoogleCid> ftidParam = GoogleCidDecoder.decodeFromUrl(ftidParamUrl);

        assertThat(encoded).isPresent();
        assertThat(encoded.get().decimal()).isEqualTo("536870922");
        assertThat(ftidParam).isEqualTo(encoded);
    }

    @Test
    void returnsEmptyForMissingZeroOrOverflowingCids() {
        assertThat(GoogleCidDecoder.decodeFromUrl(null)).isEmpty();
        assertThat(GoogleCidDecoder.decodeFromUrl("   ")).isEmpty();
        assertThat(GoogleCidDecoder.decodeFromUrl("https://example.com/img.png")).isEmpty();
        assertThat(GoogleCidDecoder.decodeFromUrl("http://maps.google.com/?cid=0")).isEmpty();
        assertThat(
                        GoogleCidDecoder.decodeFromUrl(
                                "https://www.google.com/maps/place/X/data=!1s0x1:0x0"))
                .isEmpty();
        assertThat(
                        GoogleCidDecoder.decodeFromUrl(
                                "https://www.google.com/maps/place/X/data=!1s0x1:0x10000000000000000"))
                .isEmpty();
        assertThat(
                        GoogleCidDecoder.decodeFromUrl(
                                "http://maps.google.com/?cid=18446744073709551616"))
                .isEmpty();
    }
}
