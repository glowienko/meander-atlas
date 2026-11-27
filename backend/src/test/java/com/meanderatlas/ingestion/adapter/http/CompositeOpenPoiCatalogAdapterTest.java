package com.meanderatlas.ingestion.adapter.http;

import static com.meanderatlas.ingestion.OpenCatalogTestData.COMMONS_URL;
import static com.meanderatlas.ingestion.OpenCatalogTestData.NO_FEATURES;
import static com.meanderatlas.ingestion.OpenCatalogTestData.OVERPASS_URL;
import static com.meanderatlas.ingestion.OpenCatalogTestData.WAWEL_THUMB_URL;
import static com.meanderatlas.ingestion.OpenCatalogTestData.anchoredTarget;
import static com.meanderatlas.ingestion.OpenCatalogTestData.commonsThumbResponse;
import static com.meanderatlas.ingestion.OpenCatalogTestData.overpassResponse;
import static com.meanderatlas.ingestion.OpenCatalogTestData.overpassWay;
import static com.meanderatlas.ingestion.OpenCatalogTestData.photonPoi;
import static com.meanderatlas.ingestion.OpenCatalogTestData.photonResponse;
import static com.meanderatlas.ingestion.OpenCatalogTestData.properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meanderatlas.ingestion.application.EnrichmentProperties;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.AnchoredEnrichmentTarget;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.EnrichmentLookupOutcome;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.UnanchoredLookupTarget;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.PlaceCategory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CompositeOpenPoiCatalogAdapterTest {

    private static final String WAWEL_COMMONS_QUERY =
            COMMONS_URL
                    + "?action=query&titles=File:Wawel.jpg&prop=imageinfo&iiprop=url&iiurlwidth=640&format=json&formatversion=2";
    private static final String WAWEL_OVERPASS_RESPONSE =
            overpassResponse(
                    overpassWay(
                            77,
                            50.0541,
                            19.9355,
                            Map.of(
                                    "name", "Wawel Castle",
                                    "historic", "castle",
                                    "wikimedia_commons", "File:Wawel.jpg")));

    private final RestClient.Builder overpassBuilder = RestClient.builder();
    private final RestClient.Builder photonBuilder = RestClient.builder();
    private final RestClient.Builder wikiBuilder = RestClient.builder();
    private final MockRestServiceServer overpassServer =
            MockRestServiceServer.bindTo(overpassBuilder).build();
    private final MockRestServiceServer photonServer =
            MockRestServiceServer.bindTo(photonBuilder).build();
    private final MockRestServiceServer wikiServer =
            MockRestServiceServer.bindTo(wikiBuilder).build();
    private final CompositeOpenPoiCatalogAdapter adapter = adapter(properties());

    @Test
    void usesOverpassFirstAttachesPhotoAndFallsBackToPhotonForUnmatchedTargets() {
        AnchoredEnrichmentTarget wawel =
                anchoredTarget(
                        "Wawel Castle", 50.0540, 19.9354, CoordSource.TAKEOUT_S2_APPROX, "PL");
        AnchoredEnrichmentTarget cafe =
                anchoredTarget("Cafe Camelot", 50.0630, 19.9390, CoordSource.TAKEOUT, "PL");
        overpassServer
                .expect(requestTo(OVERPASS_URL))
                .andRespond(withSuccess(WAWEL_OVERPASS_RESPONSE, MediaType.APPLICATION_JSON));
        wikiServer
                .expect(requestTo(WAWEL_COMMONS_QUERY))
                .andRespond(
                        withSuccess(
                                commonsThumbResponse(WAWEL_THUMB_URL), MediaType.APPLICATION_JSON));
        photonServer
                .expect(
                        requestTo(
                                "https://photon.example/api?q=Cafe%20Camelot&lat=50.063000&lon=19.939000&limit=5"))
                .andRespond(
                        withSuccess(
                                photonResponse(
                                        photonPoi(
                                                "N/88",
                                                "amenity=cafe",
                                                "Cafe Camelot",
                                                "Krakow",
                                                "pl",
                                                50.0631,
                                                19.9391)),
                                MediaType.APPLICATION_JSON));

        Map<?, EnrichmentLookupOutcome> outcomes =
                adapter.enrichAnchoredBatch(List.of(wawel, cafe));

        verifyAll();
        assertThat(outcomes.get(wawel.placeId()))
                .isInstanceOfSatisfying(
                        EnrichmentLookupOutcome.Matched.class,
                        matched -> {
                            assertThat(matched.match().category())
                                    .isEqualTo(PlaceCategory.LANDMARK);
                            assertThat(matched.match().photoUrl()).isEqualTo(WAWEL_THUMB_URL);
                        });
        assertThat(outcomes.get(cafe.placeId()))
                .isInstanceOfSatisfying(
                        EnrichmentLookupOutcome.Matched.class,
                        matched -> {
                            assertThat(matched.match().category())
                                    .isEqualTo(PlaceCategory.CAFE_BAR);
                            assertThat(matched.match().cityName()).isEqualTo("Krakow");
                        });
    }

    @Test
    void keepsMatchWithoutPhotoWhenWikimediaFails() {
        AnchoredEnrichmentTarget wawel =
                anchoredTarget("Wawel Castle", 50.0540, 19.9354, CoordSource.TAKEOUT, "PL");
        overpassServer
                .expect(requestTo(OVERPASS_URL))
                .andRespond(withSuccess(WAWEL_OVERPASS_RESPONSE, MediaType.APPLICATION_JSON));
        wikiServer
                .expect(requestTo(WAWEL_COMMONS_QUERY))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        EnrichmentLookupOutcome outcome =
                adapter.enrichAnchoredBatch(List.of(wawel)).get(wawel.placeId());

        verifyAll();
        assertThat(outcome)
                .isInstanceOfSatisfying(
                        EnrichmentLookupOutcome.Matched.class,
                        matched -> assertThat(matched.match().photoUrl()).isNull());
    }

    @Test
    void callsEachProviderOnceAndReportsTransientFailureWhenOverpassFailsAndPhotonHasNoMatch() {
        AnchoredEnrichmentTarget wawel =
                anchoredTarget("Wawel", 50.0540, 19.9354, CoordSource.TAKEOUT, null);
        AnchoredEnrichmentTarget eiffel =
                anchoredTarget("Eiffel Tower", 48.8584, 2.2945, CoordSource.TAKEOUT, "FR");
        overpassServer
                .expect(requestTo(OVERPASS_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        overpassServer
                .expect(requestTo(OVERPASS_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        photonServer
                .expect(
                        requestTo(
                                "https://photon.example/api?q=Eiffel%20Tower&lat=48.858400&lon=2.294500&limit=5"))
                .andRespond(
                        withSuccess(
                                photonResponse(
                                        photonPoi(
                                                "W/5013364",
                                                "tourism=attraction",
                                                "Eiffel Tower",
                                                "Paris",
                                                "fr",
                                                48.8584,
                                                2.2945)),
                                MediaType.APPLICATION_JSON));
        photonServer
                .expect(
                        requestTo(
                                "https://photon.example/api?q=Wawel&lat=50.054000&lon=19.935400&limit=5"))
                .andRespond(withSuccess(NO_FEATURES, MediaType.APPLICATION_JSON));

        Map<?, EnrichmentLookupOutcome> outcomes =
                adapter.enrichAnchoredBatch(List.of(wawel, eiffel));

        verifyAll();
        assertThat(outcomes.get(wawel.placeId()))
                .isInstanceOf(EnrichmentLookupOutcome.TransientFailure.class);
        assertThat(outcomes.get(eiffel.placeId()))
                .isInstanceOfSatisfying(
                        EnrichmentLookupOutcome.Matched.class,
                        matched -> assertThat(matched.match().cityName()).isEqualTo("Paris"));
    }

    @Test
    void reverseGeocodesCoordinateOnlyNamesAndResolvesUnanchoredTitlesViaPhoton() {
        AnchoredEnrichmentTarget coordOnly =
                anchoredTarget(
                        "52.226346,5.177603", 52.226346, 5.177603, CoordSource.TAKEOUT, "NL");
        photonServer
                .expect(
                        requestTo(
                                "https://photon.example/reverse?lat=52.226346&lon=5.177603&limit=3"))
                .andRespond(
                        withSuccess(
                                photonResponse(
                                        photonPoi(
                                                "W/7001",
                                                "historic=monument",
                                                "Marktplein Hilversum",
                                                "Hilversum",
                                                "nl",
                                                52.2263,
                                                5.1776)),
                                MediaType.APPLICATION_JSON));
        photonServer
                .expect(requestTo("https://photon.example/api?q=Colosseum&limit=5"))
                .andRespond(
                        withSuccess(
                                photonResponse(
                                        photonPoi(
                                                "W/555",
                                                "historic=monument",
                                                "Colosseum",
                                                "Rome",
                                                "it",
                                                41.8902,
                                                12.4922)),
                                MediaType.APPLICATION_JSON));

        EnrichmentLookupOutcome reversed =
                adapter.enrichAnchoredBatch(List.of(coordOnly)).get(coordOnly.placeId());
        EnrichmentLookupOutcome global =
                adapter.resolveUnanchored(
                        new UnanchoredLookupTarget(UUID.randomUUID(), "Colosseum", null, "IT"));

        verifyAll();
        assertThat(reversed)
                .isInstanceOfSatisfying(
                        EnrichmentLookupOutcome.Matched.class,
                        matched -> {
                            assertThat(matched.match().candidate().name())
                                    .isEqualTo("Marktplein Hilversum");
                            assertThat(matched.match().cityName()).isEqualTo("Hilversum");
                        });
        assertThat(global)
                .isInstanceOfSatisfying(
                        EnrichmentLookupOutcome.Matched.class,
                        matched -> assertThat(matched.match().cityName()).isEqualTo("Rome"));
    }

    private CompositeOpenPoiCatalogAdapter adapter(EnrichmentProperties props) {
        return new CompositeOpenPoiCatalogAdapter(
                new OverpassPoiClient(overpassBuilder, props),
                new PhotonPoiClient(photonBuilder, props),
                new WikimediaPhotoClient(wikiBuilder, props));
    }

    private void verifyAll() {
        overpassServer.verify();
        photonServer.verify();
        wikiServer.verify();
    }
}
