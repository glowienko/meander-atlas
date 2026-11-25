package com.meanderatlas.atlas.domain.boundaries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meanderatlas.atlas.domain.RegionLevel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NaturalEarthGeoJsonParserTest {

    @Test
    void parsesCountriesUsingIsoA2EhFallbackAndSkipsNonPolygonOrMissingCodes(@TempDir Path tempDir)
            throws IOException {
        String geoJson =
                """
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
        Path file = tempDir.resolve("ne_10m_admin_0_countries.geojson");
        Files.writeString(file, geoJson, StandardCharsets.UTF_8);

        List<BoundaryFeature> features = NaturalEarthGeoJsonParser.parseCountries(file);

        assertThat(features).hasSize(2);
        assertThat(features.getFirst().level()).isEqualTo(RegionLevel.COUNTRY);
        assertThat(features.getFirst().code()).isEqualTo("FR");
        assertThat(features.getFirst().countryCode()).isEqualTo("FR");
        assertThat(features.getFirst().name()).isEqualTo("France");
        assertThat(features.getFirst().geometryJson()).contains("\"MultiPolygon\"");

        assertThat(features.get(1).code()).isEqualTo("SOL");
        assertThat(features.get(1).countryCode()).isEqualTo("SOL");
        assertThat(features.get(1).name()).isEqualTo("Somaliland");
        assertThatThrownBy(features::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void parsesAdmin1WithAdm1CodeFallbackForInvalidTildeOrDuplicateIsoCodes() {
        String geoJson =
                """
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

        List<BoundaryFeature> features = NaturalEarthGeoJsonParser.parseAdmin1(geoJson);

        assertThat(features).hasSize(4);
        assertThat(features)
                .extracting(
                        BoundaryFeature::level,
                        BoundaryFeature::code,
                        BoundaryFeature::countryCode,
                        BoundaryFeature::name)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                RegionLevel.ADMIN1, "PL-12", "PL", "Małopolskie"),
                        org.assertj.core.groups.Tuple.tuple(
                                RegionLevel.ADMIN1, "FRA-1001", "FRA", "Corsica"),
                        org.assertj.core.groups.Tuple.tuple(
                                RegionLevel.ADMIN1, "FRA-5260", "FR", "Paris"),
                        org.assertj.core.groups.Tuple.tuple(
                                RegionLevel.ADMIN1, "FRA-5261", "FR", "Paris Outer"));
    }
}
