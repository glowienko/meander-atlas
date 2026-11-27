package com.meanderatlas.ingestion.domain.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.AnchoredEnrichmentTarget;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.OpenPoiCandidate;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.OpenPoiMatch;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.UnanchoredLookupTarget;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OpenPoiMatcherTest {

    @Test
    void matchesPolishFoldedAndBilingualNamesWithinExactAnchorRadius() {
        AnchoredEnrichmentTarget target =
                new AnchoredEnrichmentTarget(
                        PlaceId.random(),
                        "Pałac Łazienkowski",
                        new GeoPoint(52.2151, 21.0358),
                        CoordSource.TAKEOUT,
                        CategorySource.HEURISTIC,
                        null,
                        null,
                        "PL",
                        null);
        OpenPoiCandidate candidate =
                new OpenPoiCandidate(
                        "way/101",
                        "Palac Lazienkowski",
                        new GeoPoint(52.2153, 21.0360),
                        Map.of(
                                "tourism",
                                "museum",
                                "name",
                                "Pałac na Wyspie",
                                "name:en",
                                "Palac Lazienkowski"),
                        "Warszawa",
                        "PL",
                        "Tu-Su 10:00-18:00");

        Optional<OpenPoiMatch> match = OpenPoiMatcher.matchAnchored(target, List.of(candidate));

        assertThat(match).isPresent();
        assertThat(match.get().category()).isEqualTo(PlaceCategory.MUSEUM);
        assertThat(match.get().cityName()).isEqualTo("Warszawa");
        assertThat(match.get().openingHoursRaw()).isEqualTo("Tu-Su 10:00-18:00");
        assertThat(match.get().score()).isGreaterThan(0.90);
    }

    @Test
    void rejectsDistantOrLowNameSimilarityCandidatesOnExactAndApproxAnchors() {
        AnchoredEnrichmentTarget exactTarget =
                new AnchoredEnrichmentTarget(
                        PlaceId.random(),
                        "Wawel Royal Castle",
                        new GeoPoint(50.0540, 19.9354),
                        CoordSource.TAKEOUT,
                        CategorySource.HEURISTIC,
                        null,
                        null,
                        "PL",
                        null);
        OpenPoiCandidate tooFarExact =
                new OpenPoiCandidate(
                        "way/201",
                        "Wawel Royal Castle",
                        new GeoPoint(50.0620, 19.9354),
                        Map.of("historic", "castle"),
                        "Krakow",
                        "PL",
                        null);
        OpenPoiCandidate wrongNameNear =
                new OpenPoiCandidate(
                        "node/202",
                        "Biedronka Supermarket",
                        new GeoPoint(50.0541, 19.9355),
                        Map.of("shop", "supermarket"),
                        "Krakow",
                        "PL",
                        null);

        assertThat(OpenPoiMatcher.matchAnchored(exactTarget, List.of(tooFarExact, wrongNameNear)))
                .isEmpty();

        AnchoredEnrichmentTarget approxTarget =
                new AnchoredEnrichmentTarget(
                        PlaceId.random(),
                        "Miradouro da Senhora do Monte",
                        new GeoPoint(38.7190, -9.1325),
                        CoordSource.TAKEOUT_S2_APPROX,
                        CategorySource.HEURISTIC,
                        null,
                        null,
                        "PT",
                        null);
        OpenPoiCandidate withinApproxRadius =
                new OpenPoiCandidate(
                        "node/203",
                        "Miradouro da Senhora do Monte",
                        new GeoPoint(38.7260, -9.1325),
                        Map.of("tourism", "viewpoint"),
                        "Lisboa",
                        "PT",
                        null);

        Optional<OpenPoiMatch> approxMatch =
                OpenPoiMatcher.matchAnchored(approxTarget, List.of(withinApproxRadius));
        assertThat(approxMatch).isPresent();
        assertThat(approxMatch.get().category()).isEqualTo(PlaceCategory.VIEWPOINT);
    }

    @Test
    void enforcesUnanchoredThresholdCountryHintAndAmbiguityGuard() {
        UnanchoredLookupTarget target =
                new UnanchoredLookupTarget(UUID.randomUUID(), "Eiffel Tower", null, "FR");

        OpenPoiCandidate paris =
                new OpenPoiCandidate(
                        "way/301",
                        "Eiffel Tower",
                        new GeoPoint(48.8584, 2.2945),
                        Map.of("tourism", "attraction"),
                        "Paris",
                        "FR",
                        null);
        OpenPoiCandidate wrongCountry =
                new OpenPoiCandidate(
                        "node/302",
                        "Eiffel Tower",
                        new GeoPoint(36.1125, -115.1726),
                        Map.of("tourism", "attraction"),
                        "Las Vegas",
                        "US",
                        null);

        Optional<OpenPoiMatch> matched =
                OpenPoiMatcher.matchUnanchored(target, List.of(wrongCountry, paris));
        assertThat(matched).isPresent();
        assertThat(matched.get().cityName()).isEqualTo("Paris");

        UnanchoredLookupTarget noCountryHint =
                new UnanchoredLookupTarget(UUID.randomUUID(), "Eiffel Tower", null, null);
        assertThat(OpenPoiMatcher.matchUnanchored(noCountryHint, List.of(paris, wrongCountry)))
                .isEmpty();
    }

    @Test
    void rejectsPlaceholdersAndComputesSafeGeoDistance() {
        assertThat(OpenPoiMatcher.isGenericPlaceholder("Dropped Pin")).isTrue();
        assertThat(OpenPoiMatcher.isGenericPlaceholder("Reviewed place")).isTrue();
        assertThat(OpenPoiMatcher.isGenericPlaceholder("47.9201, 106.9055")).isTrue();
        assertThat(OpenPoiMatcher.isGenericPlaceholder("  ")).isTrue();
        assertThat(OpenPoiMatcher.isGenericPlaceholder("Wawel Castle")).isFalse();

        GeoPoint krakow = new GeoPoint(50.0540, 19.9354);
        assertThat(GeoDistance.metersBetween(krakow, krakow)).isCloseTo(0.0, within(1e-9));
        assertThat(
                        GeoDistance.metersBetween(
                                new GeoPoint(50.0540, 19.9354), new GeoPoint(50.0640, 19.9354)))
                .isCloseTo(1112.0, within(20.0));
    }
}
