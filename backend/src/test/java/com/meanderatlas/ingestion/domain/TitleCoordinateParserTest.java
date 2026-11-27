package com.meanderatlas.ingestion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.meanderatlas.shared.GeoPoint;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TitleCoordinateParserTest {

    private static final double TOLERANCE = 1e-6;

    @Test
    void parsesDmsDmAndDecimalCoordinateTitlesIncludingUnicodePrimes() {
        assertParses("47°55'12.3\"N 106°54'19.8\"E", 47.9200833, 106.9055);
        assertParses("47°55′12.3″N, 106°54′19.8″E", 47.9200833, 106.9055);
        assertParses("47°55’12.3”N\u00A0106°54’19.8”E", 47.9200833, 106.9055);
        assertParses("47°55'12,3\"N 106°54'19,8\"E", 47.9200833, 106.9055);
        assertParses("N 47° 55' 12.3\", E 106° 54' 19.8\"", 47.9200833, 106.9055);
        assertParses("N 47° 55' 12.3\" E 106° 54' 19.8\"", 47.9200833, 106.9055);
        assertParses("S 13° 09' 47.3\"; W 72° 32' 41.9\"", -13.1631389, -72.5449722);
        assertParses("S 13° 09' 47.3\" W 72° 32' 41.9\"", -13.1631389, -72.5449722);
        assertParses("N 47°55.205' E 106°54.330'", 47.9200833, 106.9055);
        assertParses("s 47°55.205' e 106°54.330'", -47.9200833, 106.9055);
        assertParses("N47°55'12.3\" E106°54'19.8\"", 47.9200833, 106.9055);
        assertParses("47°55.205'N 106°54.330'E", 47.9200833, 106.9055);
        assertParses("47.9201° N, 106.9055° E", 47.9201, 106.9055);
        assertParses("47.9201, 106.9055", 47.9201, 106.9055);
        assertParses("-13.1631412, -72.5449629", -13.1631412, -72.5449629);
        assertParses("47.9201°, 106.9055°", 47.9201, 106.9055);
    }

    @Test
    void rejectsNonCoordinateTitlesNullIslandAndOutOfRangeValues() {
        assertThat(TitleCoordinateParser.parse(null)).isEmpty();
        assertThat(TitleCoordinateParser.parse("")).isEmpty();
        assertThat(TitleCoordinateParser.parse("   ")).isEmpty();
        assertThat(TitleCoordinateParser.parse("Route 66")).isEmpty();
        assertThat(TitleCoordinateParser.parse("101 Restaurant")).isEmpty();
        assertThat(TitleCoordinateParser.parse("12, 34")).isEmpty();
        assertThat(TitleCoordinateParser.parse("360° Skybar")).isEmpty();
        assertThat(TitleCoordinateParser.parse("Platform 9 3/4")).isEmpty();
        assertThat(TitleCoordinateParser.parse("0.0, 0.0")).isEmpty();
        assertThat(TitleCoordinateParser.parse("0°0'0\"N 0°0'0\"E")).isEmpty();
        assertThat(TitleCoordinateParser.parse("90.1, 10.0")).isEmpty();
        assertThat(TitleCoordinateParser.parse("10.0, 180.1")).isEmpty();
        assertThat(TitleCoordinateParser.parse("47°60'12\"N 106°54'19\"E")).isEmpty();
        assertThat(TitleCoordinateParser.parse("47°55'60\"N 106°54'19\"E")).isEmpty();
        assertThat(TitleCoordinateParser.parse("47.5°55'12\"N 106°54'19\"E")).isEmpty();
        assertThat(TitleCoordinateParser.parse("47°55.5'12\"N 106°54'19\"E")).isEmpty();
        assertThat(TitleCoordinateParser.parse("47°55'12\"N 106°54'19\"N")).isEmpty();
        assertThat(TitleCoordinateParser.parse("N47°55'12\"N 106°54'19\"E")).isEmpty();
    }

    private static void assertParses(String title, double expectedLat, double expectedLng) {
        Optional<GeoPoint> parsed = TitleCoordinateParser.parse(title);
        assertThat(parsed).as(title).isPresent();
        assertThat(parsed.get().latitude()).isCloseTo(expectedLat, within(TOLERANCE));
        assertThat(parsed.get().longitude()).isCloseTo(expectedLng, within(TOLERANCE));
    }
}
