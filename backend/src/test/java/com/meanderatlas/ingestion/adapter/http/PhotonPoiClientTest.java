package com.meanderatlas.ingestion.adapter.http;

import static com.meanderatlas.ingestion.OpenCatalogTestData.photonFeature;
import static com.meanderatlas.ingestion.OpenCatalogTestData.photonPoi;
import static com.meanderatlas.ingestion.OpenCatalogTestData.photonResponse;
import static com.meanderatlas.ingestion.OpenCatalogTestData.properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meanderatlas.ingestion.adapter.http.OpenDataHttp.OpenDataResponseException;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.OpenPoiCandidate;
import com.meanderatlas.shared.GeoPoint;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PhotonPoiClientTest {

    @Test
    void queriesAnchoredAndUnanchoredAndNormalizesOsmGeoJsonFeatures() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PhotonPoiClient client = new PhotonPoiClient(builder, properties());

        server.expect(
                        requestTo(
                                "https://photon.example/api?q=Kawiarnia%20%C5%81%C3%B3d%C5%BA&lat=51.759248&lon=19.455983&limit=5"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess(
                                photonResponse(
                                        photonPoi(
                                                "N/445566",
                                                "amenity=cafe",
                                                "Kawiarnia Łódź",
                                                "Łódź",
                                                "pl",
                                                51.7593,
                                                19.4561),
                                        photonFeature(
                                                95.0, 200.0, Map.of("name", "Invalid coords"))),
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
                .andRespond(
                        withSuccess(
                                photonResponse(
                                        photonFeature(
                                                48.8584,
                                                2.2945,
                                                Map.of(
                                                        "osm_type", "W",
                                                        "osm_id", 5013364,
                                                        "osm_key", "tourism",
                                                        "osm_value", "attraction",
                                                        "name", "Eiffel Tower",
                                                        "town", "Paris",
                                                        "countrycode", "fr"))),
                                MediaType.APPLICATION_JSON));

        List<OpenPoiCandidate> global = client.search("Eiffel Tower", null);
        server.verify();
        server.reset();
        assertThat(global).hasSize(1);
        assertThat(global.getFirst().osmId()).isEqualTo("way/5013364");
        assertThat(global.getFirst().cityName()).isEqualTo("Paris");
        assertThat(client.search("   ", null)).isEmpty();
        assertThatThrownBy(() -> OpenDataHttp.readTree("{bad"))
                .isInstanceOf(OpenDataResponseException.class);
        assertThat(PhotonPoiClient.resolveReverseUrl("https://photon.example/api"))
                .isEqualTo("https://photon.example/reverse");
        assertThat(PhotonPoiClient.resolveReverseUrl("http://127.0.0.1:8080/photon"))
                .isEqualTo("http://127.0.0.1:8080/photon/reverse");

        server.expect(
                        requestTo(
                                "https://photon.example/reverse?lat=52.226346&lon=5.177603&limit=3"))
                .andRespond(
                        withSuccess(
                                photonResponse(
                                        photonPoi(
                                                "W/99001",
                                                "place=square",
                                                "Marktplein",
                                                "Hilversum",
                                                "nl",
                                                52.2263,
                                                5.1776)),
                                MediaType.APPLICATION_JSON));

        List<OpenPoiCandidate> reversed = client.reverse(new GeoPoint(52.226346, 5.177603));
        server.verify();
        assertThat(reversed).hasSize(1);
        assertThat(reversed.getFirst().name()).isEqualTo("Marktplein");
        assertThat(reversed.getFirst().cityName()).isEqualTo("Hilversum");
    }
}
