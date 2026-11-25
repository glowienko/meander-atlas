package com.meanderatlas.ingestion.domain;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

public final class TakeoutTestData {

    private TakeoutTestData() {}

    public static String savedPlaceGeoJson(
            String name, double lng, double lat, long cid, @Nullable String countryCode) {
        String countryLine =
                countryCode == null ? "" : "\"country_code\": \"" + countryCode + "\", ";
        return """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "geometry": {"type": "Point", "coordinates": [%s, %s]},
              "properties": {
                "date": "2025-05-01T10:00:00Z",
                "google_maps_url": "http://maps.google.com/?cid=%d",
                "location": {%s"name": "%s"}
              }
            }
          ]
        }
        """
                .formatted(lng, lat, cid, countryLine, name);
    }

    public static String singleVisitTimelineJson(
            String startIso, String endIso, double lat, double lng) {
        return String.format(
                Locale.ROOT,
                """
                {
                  "semanticSegments": [
                    {
                      "startTime": "%s",
                      "endTime": "%s",
                      "visit": {
                        "hierarchyLevel": 0,
                        "probability": 0.90,
                        "topCandidate": {
                          "probability": 0.90,
                          "placeLocation": {"latLng": "%.7f°, %.7f°"}
                        }
                      }
                    }
                  ]
                }
                """,
                startIso,
                endIso,
                lat,
                lng);
    }

    public static String savedCsv(String... rows) {
        return "Title,Note,URL,Tags,Comment\n" + String.join("\n", rows) + "\n";
    }

    public static String nullIslandReviewsGeoJson() {
        return """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "geometry": { "type": "Point", "coordinates": [0, 0] },
              "properties": {
                "date": "2025-07-01T08:00:00Z",
                "five_star_rating_published": 4,
                "review_text_published": "Great ramen.",
                "google_maps_url": "http://maps.google.com/?cid=18446744073709551615",
                "location": { "name": "Ichiran Shibuya", "country_code": "JP" }
              }
            },
            {
              "type": "Feature",
              "geometry": { "type": "Point", "coordinates": [0.0, 0.0] },
              "properties": {
                "date": "2024-01-01T00:00:00Z"
              }
            }
          ]
        }
        """;
    }

    public static String malformedSavedPlacesGeoJson() {
        return """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "geometry": { "type": "Point", "coordinates": ["bad", 50.0] },
              "properties": { "Title": "Bad X" }
            },
            {
              "type": "Feature",
              "geometry": { "type": "Point", "coordinates": [19.0, 95.0] },
              "properties": { "Title": "Bad Lat" }
            },
            {
              "type": "Feature",
              "geometry": { "type": "Point", "coordinates": [19.9, 50.0] },
              "properties": { "date": "2025-05-01T10:00:00Z" }
            },
            {
              "type": "Feature",
              "geometry": { "type": "Point", "coordinates": [19.9354, 50.0540] },
              "properties": {
                "Published": "not-an-instant",
                "Google Maps URL": "http://maps.google.com/?cid=536870920",
                "Title": "Legacy Keys Spot"
              }
            }
          ]
        }
        """;
    }

    public static String malformedReviewsGeoJson() {
        return """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "geometry": { "type": "Point", "coordinates": [19.9, 50.0] },
              "properties": {
                "date": "not-an-instant",
                "five_star_rating_published": 9,
                "location": { "name": "Invalid Rating Cafe" }
              }
            },
            {
              "type": "Feature",
              "geometry": { "type": "Point", "coordinates": [19.9] },
              "properties": {
                "five_star_rating_published": "5",
                "location": { "name": "Short Coords" }
              }
            }
          ]
        }
        """;
    }

    public static String negativeAndInvalidTimelineJson() {
        return """
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
    }

    public static String badTimestampTimelineJson() {
        return """
        {
          "semanticSegments": [
            {
              "startTime": "bad-ts",
              "endTime": "2026-09-02T11:00:00Z",
              "visit": { "topCandidate": { "placeLocation": { "latLng": "50.0°, 19.9°" } } }
            },
            {
              "endTime": "2026-09-02T11:00:00Z",
              "visit": { "topCandidate": { "placeLocation": { "latLng": "50.0°, 19.9°" } } }
            }
          ]
        }
        """;
    }

    public static String nbspBomAndQuotedMultilineCsv() {
        return "\uFEFFCustom list preamble line 1\n"
                + "Second description line, with comma\n"
                + "\n"
                + "Tytuł\u00A0,Notatka\u00A0,URL\u00A0,Tagi,Skomentuj\n"
                + "\"Cafe \"\"Roma\"\", Old Town\",\"Line 1\nLine 2\","
                + "https://www.google.com/maps/place/Cafe+Roma/data=!4m2!3m1!1s0x1:0xf123456789abcdef,"
                + "coffee,\"Best espresso, ever\"\n"
                + "Non-Maps Bookmark,ignore,https://example.com/article,web,\n"
                + "Spoofed Query,ignore,https://example.com/?ref=google.com/maps,web,\n"
                + "Regional Maps,,https://www.google.pl/maps/place/Test/data=!4m2!3m1!1s0x1:0x20000001,,\n";
    }

    public static String crlfAndNoTrailingNewlineCsv() {
        return String.join(
                "\r\n",
                "Title,Note,URL,Tags,Comment",
                "Wawel Royal Castle,Hilltop,https://www.google.com/maps/place/Wawel/data=!4m2!3m1!1s0x1:0x20000001,castle,",
                "Eiffel Tower,Iron tower,https://www.google.com/maps/place/Eiffel/data=!4m2!3m1!1s0x1:0x20000003,tower,");
    }
}
