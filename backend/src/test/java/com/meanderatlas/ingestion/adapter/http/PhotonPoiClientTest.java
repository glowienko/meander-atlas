package com.meanderatlas.ingestion.adapter.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.OpenPoiCandidate;
import com.meanderatlas.shared.GeoPoint;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PhotonPoiClientTest {

    private static EnrichmentProperties testProperties() {
        return new EnrichmentProperties(
                true,
                25,
                0,
                2,
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
    void queriesAnchoredAndUnanchoredAndNormalizesOsmGeoJsonFeatures() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PhotonPoiClient client = new PhotonPoiClient(builder, testProperties());

        server.expect(
                        requestTo(
                                "https://photon.example/api?q=Kawiarnia%20%C5%81%C3%B3d%C5%BA&lat=51.759248&lon=19.455983&limit=5"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "type": "FeatureCollection",
                                  "features": [
                                    {
                                      "type": "Feature",
                                      "geometry": { "type": "Point", "coordinates": [19.4561, 51.7593] },
                                      "properties": {
                                        "osm_type": "N",
                                        "osm_id": 445566,
                                        "osm_key": "amenity",
                                        "osm_value": "cafe",
                                        "name": "Kawiarnia Łódź",
                                        "city": "Łódź",
                                        "countrycode": "pl"
                                      }
                                    },
                                    {
                                      "type": "Feature",
                                      "geometry": { "type": "Point", "coordinates": [200.0, 95.0] },
                                      "properties": { "name": "Invalid coords" }
                                    }
                                  ]
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        List<OpenPoiCandidate> anchored =
                client.search("Kawiarnia Łódź", new GeoPoint(51.759248, 19.455983));
        server.verify();
        server.reset();
        assertThat(anchored).hasSize(1);
        assertThat(anchored.getFirst().osmId()).isEqualTo("node/445566");
        assertThat(anchored.getFirst().tags()).containsEntry("amenity", "cafe");
        assertThat(anchored.getFirst().countryCode()).isEqualTo("PL");
        assertThat(anchored.getFirst().cityName()).isEqualTo("Łódź");

        server.expect(requestTo("https://photon.example/api?q=Eiffel%20Tower&limit=5"))
                .andExpect(method(HttpMethod.GET))
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
                                        "town": "Paris",
                                        "countrycode": "fr"
                                      }
                                    }
                                  ]
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        List<OpenPoiCandidate> global = client.search("Eiffel Tower", null);
        server.verify();

        assertThat(global).hasSize(1);
        assertThat(global.getFirst().osmId()).isEqualTo("way/5013364");
        assertThat(global.getFirst().cityName()).isEqualTo("Paris");
        assertThat(client.search("   ", null)).isEmpty();
        assertThat(PhotonPoiClient.parseResponse("{bad")).isEmpty();
        assertThat(PhotonPoiClient.resolveReverseUrl("https://photon.example/api"))
                .isEqualTo("https://photon.example/reverse");
        assertThat(PhotonPoiClient.resolveReverseUrl("http://127.0.0.1:8080/photon"))
                .isEqualTo("http://127.0.0.1:8080/photon/reverse");

        server.reset();
        server.expect(
                        requestTo(
                                "https://photon.example/reverse?lat=52.226346&lon=5.177603&limit=3"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "features": [
                                    {
                                      "geometry": { "coordinates": [5.1776, 52.2263] },
                                      "properties": {
                                        "osm_type": "W",
                                        "osm_id": 99001,
                                        "osm_key": "place",
                                        "osm_value": "square",
                                        "name": "Marktplein",
                                        "city": "Hilversum",
                                        "countrycode": "nl"
                                      }
                                    }
                                  ]
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        List<OpenPoiCandidate> reversed = client.reverse(new GeoPoint(52.226346, 5.177603));
        server.verify();
        assertThat(reversed).hasSize(1);
        assertThat(reversed.getFirst().name()).isEqualTo("Marktplein");
        assertThat(reversed.getFirst().cityName()).isEqualTo("Hilversum");
    }
}
