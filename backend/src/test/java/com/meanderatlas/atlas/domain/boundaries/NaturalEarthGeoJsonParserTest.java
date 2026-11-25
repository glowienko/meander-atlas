package com.meanderatlas.atlas.domain.boundaries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meanderatlas.atlas.domain.RegionLevel;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NaturalEarthGeoJsonParserTest {

    @Test
    void parsesCountriesUsingIsoA2EhFallbackAndSkipsNonPolygonOrMissingCodes(@TempDir Path tempDir)
            throws IOException {
        Path file =
                BoundaryTestData.writeGeoJson(
                        tempDir,
                        "ne_10m_admin_0_countries.geojson",
                        BoundaryTestData.countriesWithFallbacksGeoJson());

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
    void parsesAdmin1WithAdm1CodeFallbackForInvalidTildeOrDuplicateIsoCodes(@TempDir Path tempDir)
            throws IOException {
        Path file =
                BoundaryTestData.writeGeoJson(
                        tempDir,
                        "ne_10m_admin_1_states_provinces.geojson",
                        BoundaryTestData.admin1WithDuplicateAndTildeIsoGeoJson());

        List<BoundaryFeature> features = NaturalEarthGeoJsonParser.parseAdmin1(file);

        assertThat(features)
                .hasSize(4)
                .extracting(
                        BoundaryFeature::level,
                        BoundaryFeature::code,
                        BoundaryFeature::countryCode,
                        BoundaryFeature::name)
                .containsExactly(
                        Tuple.tuple(RegionLevel.ADMIN1, "PL-12", "PL", "Małopolskie"),
                        Tuple.tuple(RegionLevel.ADMIN1, "FRA-1001", "FR", "Corsica"),
                        Tuple.tuple(RegionLevel.ADMIN1, "FRA-5260", "FR", "Paris"),
                        Tuple.tuple(RegionLevel.ADMIN1, "FRA-5261", "FR", "Paris Outer"));
    }

    @Test
    void derivesAdmin1CountryFromIso3166AndRejectsBlankBoundaryFields(@TempDir Path tempDir)
            throws IOException {
        Path invalidCountries =
                BoundaryTestData.writeGeoJson(
                        tempDir, "invalid.geojson", BoundaryTestData.invalidCountryCodesGeoJson());
        Path emptyObject = BoundaryTestData.writeGeoJson(tempDir, "empty.geojson", "{}");
        Path emptyArray = BoundaryTestData.writeGeoJson(tempDir, "empty-arr.geojson", "[]");
        Path admin1File =
                BoundaryTestData.writeGeoJson(
                        tempDir,
                        "admin1-iso.geojson",
                        BoundaryTestData.admin1IsoCountryDerivationGeoJson());

        assertThat(NaturalEarthGeoJsonParser.parseCountries(invalidCountries)).isEmpty();
        assertThat(NaturalEarthGeoJsonParser.parseCountries(emptyObject)).isEmpty();
        assertThat(NaturalEarthGeoJsonParser.parseAdmin1(emptyArray)).isEmpty();

        List<BoundaryFeature> admin1 = NaturalEarthGeoJsonParser.parseAdmin1(admin1File);
        assertThat(admin1)
                .extracting(BoundaryFeature::code, BoundaryFeature::countryCode)
                .containsExactly(Tuple.tuple("GB-ENG", "GB"), Tuple.tuple("SOL-01", "SOL"));

        assertThatThrownBy(
                        () -> new BoundaryFeature(RegionLevel.COUNTRY, " ", "PL", "Poland", "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new BoundaryFeature(RegionLevel.COUNTRY, "PL", " ", "Poland", "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoundaryFeature(RegionLevel.COUNTRY, "PL", "PL", " ", "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new BoundaryFeature(RegionLevel.COUNTRY, "PL", "PL", "Poland", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
