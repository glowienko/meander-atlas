package com.meanderatlas.ingestion.adapter.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.AnchoredEnrichmentTarget;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.EnrichmentLookupOutcome;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.UnanchoredLookupTarget;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CompositeOpenPoiCatalogAdapterTest {

    private static EnrichmentProperties propertiesWithDelay(long delayMs, int maxRetries) {
        return new EnrichmentProperties(
                true,
                25,
                delayMs,
                maxRetries,
                1000,
                5000,
                "https://overpass.example/api/interpreter",
                "https://photon.example/api",
                "https://commons.example/w/api.php",
                "https://{lang}.wikipedia.example/api/rest_v1",
                "https://wikidata.example/w/api.php",
                "MeanderAtlas/0.1-test");
    }

    @Test
    void usesOverpassFirstFallsBackToPhotonAndRecoversFrom429WithRetryAfter() {
        RestClient.Builder overpassBuilder = RestClient.builder();
        MockRestServiceServer overpassServer =
                MockRestServiceServer.bindTo(overpassBuilder).build();
        RestClient.Builder photonBuilder = RestClient.builder();
        MockRestServiceServer photonServer = MockRestServiceServer.bindTo(photonBuilder).build();
        RestClient.Builder wikiBuilder = RestClient.builder();
        MockRestServiceServer wikiServer = MockRestServiceServer.bindTo(wikiBuilder).build();

        EnrichmentProperties props = propertiesWithDelay(10L, 2);
        List<Long> sleeps = new ArrayList<>();
        CompositeOpenPoiCatalogAdapter adapter =
                new CompositeOpenPoiCatalogAdapter(
                        new OverpassPoiClient(overpassBuilder, props),
                        new PhotonPoiClient(photonBuilder, props),
                        new WikimediaPhotoClient(wikiBuilder, props),
                        props,
                        sleeps::add);

        PlaceId wawelId = PlaceId.random();
        PlaceId cafeId = PlaceId.random();
        AnchoredEnrichmentTarget wawel =
                new AnchoredEnrichmentTarget(
                        wawelId,
                        "Wawel Castle",
                        new GeoPoint(50.0540, 19.9354),
                        CoordSource.TAKEOUT_S2_APPROX,
                        CategorySource.HEURISTIC,
                        null,
                        null,
                        "PL",
                        null);
        AnchoredEnrichmentTarget cafe =
                new AnchoredEnrichmentTarget(
                        cafeId,
                        "Cafe Camelot",
                        new GeoPoint(50.0630, 19.9390),
                        CoordSource.TAKEOUT,
                        CategorySource.HEURISTIC,
                        null,
                        null,
                        "PL",
                        null);

        HttpHeaders retryHeaders = new HttpHeaders();
        retryHeaders.set(HttpHeaders.RETRY_AFTER, "1");
        overpassServer
                .expect(requestTo("https://overpass.example/api/interpreter"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(retryHeaders));
        overpassServer
                .expect(requestTo("https://overpass.example/api/interpreter"))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "elements": [
                                    {
                                      "type": "way",
                                      "id": 77,
                                      "center": { "lat": 50.0541, "lon": 19.9355 },
                                      "tags": {
                                        "name": "Wawel Castle",
                                        "historic": "castle",
                                        "wikimedia_commons": "File:Wawel.jpg"
                                      }
                                    }
                                  ]
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        wikiServer
                .expect(
                        requestTo(
                                "https://commons.example/w/api.php?action=query&titles=File:Wawel.jpg&prop=imageinfo&iiprop=url&iiurlwidth=640&format=json&formatversion=2"))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "query": {
                                    "pages": [
                                      { "imageinfo": [{ "thumburl": "https://upload.wikimedia.org/wikipedia/commons/thumb/w/Wawel.jpg" }] }
                                    ]
                                  }
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        photonServer
                .expect(
                        requestTo(
                                "https://photon.example/api?q=Cafe%20Camelot&lat=50.063000&lon=19.939000&limit=5"))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "features": [
                                    {
                                      "geometry": { "coordinates": [19.9391, 50.0631] },
                                      "properties": {
                                        "osm_type": "N",
                                        "osm_id": 88,
                                        "osm_key": "amenity",
                                        "osm_value": "cafe",
                                        "name": "Cafe Camelot",
                                        "city": "Krakow",
                                        "countrycode": "pl"
                                      }
                                    }
                                  ]
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        Map<PlaceId, EnrichmentLookupOutcome> outcomes =
                adapter.enrichAnchoredBatch(List.of(wawel, cafe));

        overpassServer.verify();
        photonServer.verify();
        wikiServer.verify();

        assertThat(sleeps).contains(1000L, 10L);
        assertThat(outcomes.get(wawelId))
                .isInstanceOfSatisfying(
                        EnrichmentLookupOutcome.Matched.class,
                        matched -> {
                            assertThat(matched.match().category())
                                    .isEqualTo(PlaceCategory.LANDMARK);
                            assertThat(matched.match().photoUrl())
                                    .isEqualTo(
                                            "https://upload.wikimedia.org/wikipedia/commons/thumb/w/Wawel.jpg");
                        });
        assertThat(outcomes.get(cafeId))
                .isInstanceOfSatisfying(
                        EnrichmentLookupOutcome.Matched.class,
                        matched -> {
                            assertThat(matched.match().category())
                                    .isEqualTo(PlaceCategory.CAFE_BAR);
                            assertThat(matched.match().cityName()).isEqualTo("Krakow");
                        });
    }

    @Test
    void returnsTransientFailureAfterExhaustingRetriesAndResolvesUnanchoredViaPhoton() {
        RestClient.Builder overpassBuilder = RestClient.builder();
        MockRestServiceServer overpassServer =
                MockRestServiceServer.bindTo(overpassBuilder).build();
        RestClient.Builder photonBuilder = RestClient.builder();
        MockRestServiceServer photonServer = MockRestServiceServer.bindTo(photonBuilder).build();
        RestClient.Builder wikiBuilder = RestClient.builder();
        MockRestServiceServer wikiServer = MockRestServiceServer.bindTo(wikiBuilder).build();

        EnrichmentProperties props = propertiesWithDelay(0L, 1);
        CompositeOpenPoiCatalogAdapter adapter =
                new CompositeOpenPoiCatalogAdapter(
                        new OverpassPoiClient(overpassBuilder, props),
                        new PhotonPoiClient(photonBuilder, props),
                        new WikimediaPhotoClient(wikiBuilder, props),
                        props,
                        _ -> {});

        PlaceId failId = PlaceId.random();
        PlaceId secondCellId = PlaceId.random();
        AnchoredEnrichmentTarget target =
                new AnchoredEnrichmentTarget(
                        failId,
                        "Wawel",
                        new GeoPoint(50.0540, 19.9354),
                        CoordSource.TAKEOUT,
                        CategorySource.HEURISTIC,
                        null,
                        null,
                        null,
                        null);
        AnchoredEnrichmentTarget secondCellTarget =
                new AnchoredEnrichmentTarget(
                        secondCellId,
                        "Eiffel Tower",
                        new GeoPoint(48.8584, 2.2945),
                        CoordSource.TAKEOUT,
                        CategorySource.HEURISTIC,
                        null,
                        null,
                        "FR",
                        null);

        overpassServer
                .expect(requestTo("https://overpass.example/api/interpreter"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        overpassServer
                .expect(requestTo("https://overpass.example/api/interpreter"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        photonServer
                .expect(
                        requestTo(
                                "https://photon.example/api?q=Eiffel%20Tower&lat=48.858400&lon=2.294500&limit=5"))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "features": [
                                    {
                                      "geometry": { "coordinates": [2.2945, 48.8584] },
                                      "properties": {
                                        "osm_type": "W",
                                        "osm_id": 5013364,
                                        "osm_key": "tourism",
                                        "osm_value": "attraction",
                                        "name": "Eiffel Tower",
                                        "city": "Paris",
                                        "countrycode": "fr"
                                      }
                                    }
                                  ]
                                }
                                """,
                                MediaType.APPLICATION_JSON));
        photonServer
                .expect(
                        requestTo(
                                "https://photon.example/api?q=Wawel&lat=50.054000&lon=19.935400&limit=5"))
                .andRespond(withSuccess("{\"features\":[]}", MediaType.APPLICATION_JSON));

        Map<PlaceId, EnrichmentLookupOutcome> failedOutcomes =
                adapter.enrichAnchoredBatch(List.of(target, secondCellTarget));
        assertThat(failedOutcomes.get(failId))
                .isInstanceOf(EnrichmentLookupOutcome.TransientFailure.class);
        assertThat(failedOutcomes.get(secondCellId))
                .isInstanceOfSatisfying(
                        EnrichmentLookupOutcome.Matched.class,
                        matched -> assertThat(matched.match().cityName()).isEqualTo("Paris"));
        overpassServer.verify();
        photonServer.verify();
        overpassServer.reset();
        photonServer.reset();

        PlaceId coordOnlyId = PlaceId.random();
        AnchoredEnrichmentTarget coordOnlyTarget =
                new AnchoredEnrichmentTarget(
                        coordOnlyId,
                        "52.226346,5.177603",
                        new GeoPoint(52.226346, 5.177603),
                        CoordSource.TAKEOUT,
                        CategorySource.HEURISTIC,
                        null,
                        null,
                        "NL",
                        null);
        photonServer
                .expect(
                        requestTo(
                                "https://photon.example/reverse?lat=52.226346&lon=5.177603&limit=3"))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "features": [
                                    {
                                      "geometry": { "coordinates": [5.1776, 52.2263] },
                                      "properties": {
                                        "osm_type": "W",
                                        "osm_id": 7001,
                                        "osm_key": "historic",
                                        "osm_value": "monument",
                                        "name": "Marktplein Hilversum",
                                        "city": "Hilversum",
                                        "countrycode": "nl"
                                      }
                                    }
                                  ]
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        Map<PlaceId, EnrichmentLookupOutcome> reversedOutcomes =
                adapter.enrichAnchoredBatch(List.of(coordOnlyTarget));
        assertThat(reversedOutcomes.get(coordOnlyId))
                .isInstanceOfSatisfying(
                        EnrichmentLookupOutcome.Matched.class,
                        matched -> {
                            assertThat(matched.match().candidate().name())
                                    .isEqualTo("Marktplein Hilversum");
                            assertThat(matched.match().cityName()).isEqualTo("Hilversum");
                        });
        photonServer.verify();
        photonServer.reset();

        photonServer
                .expect(requestTo("https://photon.example/api?q=Colosseum&limit=5"))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "features": [
                                    {
                                      "geometry": { "coordinates": [12.4922, 41.8902] },
                                      "properties": {
                                        "osm_type": "W",
                                        "osm_id": 555,
                                        "osm_key": "historic",
                                        "osm_value": "monument",
                                        "name": "Colosseum",
                                        "city": "Rome",
                                        "countrycode": "it"
                                      }
                                    }
                                  ]
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        EnrichmentLookupOutcome globalOutcome =
                adapter.resolveUnanchored(
                        new UnanchoredLookupTarget(UUID.randomUUID(), "Colosseum", null, "IT"));
        assertThat(globalOutcome)
                .isInstanceOfSatisfying(
                        EnrichmentLookupOutcome.Matched.class,
                        matched -> assertThat(matched.match().cityName()).isEqualTo("Rome"));

        overpassServer.verify();
        photonServer.verify();
        wikiServer.verify();
    }
}
