package com.meanderatlas.atlas.domain.boundaries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BoundaryTestData {

    private static final String COUNTRIES_FILE = "ne_10m_admin_0_countries.geojson";
    private static final String ADMIN1_FILE = "ne_10m_admin_1_states_provinces.geojson";
    private static final String POLAND_COUNTRY_FEATURE =
            """
            {
              "type": "Feature",
              "properties": {"ISO_A2_EH": "PL", "ADMIN": "Poland"},
              "geometry": {
                "type": "MultiPolygon",
                "coordinates": [[[[18.0, 49.0], [22.0, 49.0], [22.0, 54.4], [18.0, 54.4], [18.0, 49.0]]]]
              }
            }
            """;

    private BoundaryTestData() {}

    public static void writePolandAndAntarctica(Path dir) throws IOException {
        writeGeoJson(dir, COUNTRIES_FILE, polandAndAntarcticaGeoJson());
        writeGeoJson(dir, ADMIN1_FILE, singleMalopolskieAdmin1GeoJson());
    }

    public static void writePolandOnly(Path dir) throws IOException {
        writeGeoJson(dir, COUNTRIES_FILE, singlePolandCountryGeoJson());
        writeGeoJson(dir, ADMIN1_FILE, singleMalopolskieAdmin1GeoJson());
    }

    public static Path writeGeoJson(Path dir, String fileName, String content) throws IOException {
        Files.createDirectories(dir);
        Path target = dir.resolve(fileName);
        Files.writeString(target, content, StandardCharsets.UTF_8);
        return target;
    }

    public static String polandAndAntarcticaGeoJson() {
        return featureCollection(
                POLAND_COUNTRY_FEATURE,
                """
                {
                  "type": "Feature",
                  "properties": {"ISO_A2_EH": "AQ", "ADMIN": "Antarctica"},
                  "geometry": {
                    "type": "Polygon",
                    "coordinates": [[[-180.0, -89.9], [180.0, -89.9], [180.0, -70.0], [-180.0, -70.0], [-180.0, -89.9]]]
                  }
                }
                """);
    }

    public static String singlePolandCountryGeoJson() {
        return featureCollection(POLAND_COUNTRY_FEATURE);
    }

    private static String featureCollection(String... features) {
        return """
        {
          "type": "FeatureCollection",
          "features": [
            %s
          ]
        }
        """
                .formatted(String.join(",\n", features));
    }

    public static String singleMalopolskieAdmin1GeoJson() {
        return """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "properties": {"iso_3166_2": "PL-12", "iso_a2": "PL", "name": "Małopolskie"},
              "geometry": {
                "type": "Polygon",
                "coordinates": [[[19.0, 49.4], [21.2, 49.4], [21.2, 50.6], [19.0, 50.6], [19.0, 49.4]]]
              }
            }
          ]
        }
        """;
    }

    public static String countriesWithFallbacksGeoJson() {
        return """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "properties": {
                "ISO_A2": "-99",
                "ISO_A2_EH": "FR",
                "ADM0_A3": "FRA",
                "ADMIN": "France",
                "NAME": "France"
              },
              "geometry": {
                "type": "MultiPolygon",
                "coordinates": [[[[2.0, 48.0], [3.0, 48.0], [3.0, 49.0], [2.0, 49.0], [2.0, 48.0]]]]
              }
            },
            {
              "type": "Feature",
              "properties": {
                "ISO_A2": "-99",
                "ISO_A2_EH": "-99",
                "ADM0_A3": "SOL",
                "NAME": "Somaliland"
              },
              "geometry": {
                "type": "Polygon",
                "coordinates": [[[44.0, 9.0], [45.0, 9.0], [45.0, 10.0], [44.0, 10.0], [44.0, 9.0]]]
              }
            },
            {
              "type": "Feature",
              "properties": {
                "ISO_A2": "PL",
                "ADMIN": "Poland"
              },
              "geometry": {
                "type": "Point",
                "coordinates": [19.9, 50.0]
              }
            },
            {
              "type": "Feature",
              "properties": {
                "ISO_A2": "DE",
                "ADMIN": "Germany"
              },
              "geometry": null
            }
          ]
        }
        """;
    }

    public static String admin1WithDuplicateAndTildeIsoGeoJson() {
        return """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "properties": {
                "iso_3166_2": "PL-12",
                "adm1_code": "POL-3142",
                "iso_a2": "PL",
                "name": "Małopolskie"
              },
              "geometry": {
                "type": "Polygon",
                "coordinates": [[[19.0, 49.5], [21.0, 49.5], [21.0, 50.5], [19.0, 50.5], [19.0, 49.5]]]
              }
            },
            {
              "type": "Feature",
              "properties": {
                "iso_3166_2": "FR-X~",
                "adm1_code": "FRA-1001",
                "iso_a2": "-99",
                "adm0_a3": "FRA",
                "name_en": "Corsica"
              },
              "geometry": {
                "type": "Polygon",
                "coordinates": [[[8.5, 41.3], [9.6, 41.3], [9.6, 43.0], [8.5, 43.0], [8.5, 41.3]]]
              }
            },
            {
              "type": "Feature",
              "properties": {
                "iso_3166_2": "FR-75",
                "adm1_code": "FRA-5260",
                "iso_a2": "FR",
                "name": "Paris"
              },
              "geometry": {
                "type": "Polygon",
                "coordinates": [[[2.2, 48.8], [2.4, 48.8], [2.4, 48.9], [2.2, 48.9], [2.2, 48.8]]]
              }
            },
            {
              "type": "Feature",
              "properties": {
                "iso_3166_2": "FR-75",
                "adm1_code": "FRA-5261",
                "iso_a2": "FR",
                "name": "Paris Outer"
              },
              "geometry": {
                "type": "Polygon",
                "coordinates": [[[2.1, 48.7], [2.2, 48.7], [2.2, 48.8], [2.1, 48.8], [2.1, 48.7]]]
              }
            }
          ]
        }
        """;
    }

    public static String invalidCountryCodesGeoJson() {
        return """
        {
          "type": "FeatureCollection",
          "ignored": [1, 2],
          "features": [
            {
              "type": "Feature",
              "properties": {"ISO_A2": "-99", "ADM0_A3": "-99"},
              "geometry": {"type": "Polygon", "coordinates": [[[0,0],[1,0],[1,1],[0,1],[0,0]]]}
            }
          ]
        }
        """;
    }

    public static String admin1IsoCountryDerivationGeoJson() {
        return """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "properties": {"iso_3166_2": "GB-ENG", "iso_a2": "-99", "name": "England"},
              "geometry": {"type": "Polygon", "coordinates": [[[-2,51],[1,51],[1,54],[-2,54],[-2,51]]]}
            },
            {
              "type": "Feature",
              "properties": {"iso_3166_2": "GB-SCT", "iso_a2": "-99", "name": "   "},
              "geometry": {"type": "Polygon", "coordinates": [[[-4,55],[-2,55],[-2,58],[-4,58],[-4,55]]]}
            },
            {
              "type": "Feature",
              "properties": {
                "iso_3166_2": "-99",
                "adm1_code": "SOL-01",
                "iso_a2": "-99",
                "adm0_a3": "SOL",
                "name": "Maroodi Jeex"
              },
              "geometry": {"type": "Polygon", "coordinates": [[[44,9],[45,9],[45,10],[44,10],[44,9]]]}
            }
          ]
        }
        """;
    }
}
