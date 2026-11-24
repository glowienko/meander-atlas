package com.meanderatlas.ingestion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.meanderatlas.shared.GeoPoint;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimelineParserTest {

    @Test
    void parsesAllVisitSegmentsFromSyntheticTimelineAndSkipsActivities() throws IOException {
        List<ParsedTimelineVisit> visits =
                TimelineParser.parse(FixtureArchiveSupport.syntheticTimelinePath());

        assertThat(visits).hasSize(10);

        ParsedTimelineVisit wawel = visits.getFirst();
        assertThat(wawel.googlePlaceId()).isEqualTo("ChIJ0-synthetic-wawel-01");
        assertThat(wawel.coordinates()).isEqualTo(new GeoPoint(50.054049, 19.935412));
        assertThat(wawel.startTime()).isEqualTo(Instant.parse("2026-08-10T07:00:00Z"));
        assertThat(wawel.endTime()).isEqualTo(Instant.parse("2026-08-10T09:30:00Z"));
        assertThat(wawel.semanticType()).isEqualTo("UNKNOWN");
        assertThat(wawel.visitProbability()).isEqualTo(0.95);
        assertThat(wawel.candidateProbability()).isEqualTo(0.92);

        ParsedTimelineVisit goldenGate = visits.get(8);
        assertThat(goldenGate.googlePlaceId()).isEqualTo("ChIJ0-synthetic-ggb-09");
        assertThat(goldenGate.coordinates()).isEqualTo(new GeoPoint(37.8199286, -122.4782551));

        ParsedTimelineVisit liberty = visits.getLast();
        assertThat(liberty.googlePlaceId()).isEqualTo("ChIJ0-synthetic-liberty-10");
        assertThat(liberty.coordinates()).isEqualTo(new GeoPoint(40.6892494, -74.0445004));
    }

    @Test
    void parsesNegativeLatitudeAndLongitudeWithDegreeSymbolsAndSkipsInvalidSegments() {
        String json =
                """
                {
                  "semanticSegments": [
                    {
                      "startTime": "2026-09-01T14:00:00.000-05:00",
                      "endTime": "2026-09-01T17:00:00.000-05:00",
                      "visit": {
                        "probability": 0.91,
                        "topCandidate": {
                          "placeId": "ChIJ-machu-picchu",
                          "semanticType": "UNKNOWN",
                          "probability": 0.89,
                          "placeLocation": {
                            "latLng": " -13.1631412° , -72.5449629° "
                          }
                        }
                      }
                    },
                    {
                      "startTime": "2026-09-02T10:00:00Z",
                      "endTime": "2026-09-02T11:00:00Z",
                      "visit": {
                        "topCandidate": {
                          "placeId": "ChIJ-null-island",
                          "placeLocation": {
                            "latLng": "0.0°, 0.0°"
                          }
                        }
                      }
                    },
                    {
                      "startTime": "2026-09-03T10:00:00Z",
                      "endTime": "2026-09-03T11:00:00Z",
                      "timelinePath": []
                    }
                  ]
                }
                """;

        List<ParsedTimelineVisit> visits = TimelineParser.parse("Timeline.json", json);

        assertThat(visits).hasSize(1);
        ParsedTimelineVisit machuPicchu = visits.getFirst();
        assertThat(machuPicchu.googlePlaceId()).isEqualTo("ChIJ-machu-picchu");
        assertThat(machuPicchu.coordinates()).isEqualTo(new GeoPoint(-13.1631412, -72.5449629));
        assertThat(machuPicchu.startTime()).isEqualTo(Instant.parse("2026-09-01T19:00:00Z"));
        assertThat(machuPicchu.endTime()).isEqualTo(Instant.parse("2026-09-01T22:00:00Z"));

        assertThat(TimelineParser.parseDegreeLatLng("-33.8567844°, 151.2152967°"))
                .contains(new GeoPoint(-33.8567844, 151.2152967));
        assertThat(TimelineParser.parseDegreeLatLng("50.054049, 19.935412"))
                .contains(new GeoPoint(50.054049, 19.935412));
        assertThat(TimelineParser.parseDegreeLatLng("0°, 0°")).isEmpty();
        assertThat(TimelineParser.parseDegreeLatLng("95.0°, 10.0°")).isEmpty();
        assertThat(TimelineParser.parseDegreeLatLng("not-a-coord")).isEmpty();
        assertThat(TimelineParser.parseDegreeLatLng(null)).isEmpty();
    }
}
