package com.meanderatlas.ingestion.adapter.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meanderatlas.ingestion.adapter.http.OverpassPoiClient.OverpassTransientException;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.AnchoredEnrichmentTarget;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.OpenPoiCandidate;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.PlaceId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

class OverpassPoiClientTest {

    private static EnrichmentProperties testProperties() {
        return new EnrichmentProperties(
                true,
                2,
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
    void partitionsIntoSpatialCellsAndParsesNodesAndWayCenters() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OverpassPoiClient client = new OverpassPoiClient(builder, testProperties());

        AnchoredEnrichmentTarget krakow =
                new AnchoredEnrichmentTarget(
                        PlaceId.random(),
                        "Wawel",
                        new GeoPoint(50.0540, 19.9354),
                        CoordSource.TAKEOUT,
                        CategorySource.HEURISTIC,
                        null,
                        null,
                        "PL",
                        null);
        AnchoredEnrichmentTarget warsaw =
                new AnchoredEnrichmentTarget(
                        PlaceId.random(),
                        "Lazienki",
                        new GeoPoint(52.2151, 21.0358),
                        CoordSource.TAKEOUT_S2_APPROX,
                        CategorySource.HEURISTIC,
                        null,
                        null,
                        "PL",
                        null);
        AnchoredEnrichmentTarget krakow2 =
                new AnchoredEnrichmentTarget(
                        PlaceId.random(),
                        "Sukiennice",
                        new GeoPoint(50.0617, 19.9373),
                        CoordSource.TAKEOUT,
                        CategorySource.HEURISTIC,
                        null,
                        null,
                        "PL",
                        null);

        List<List<AnchoredEnrichmentTarget>> batches =
                client.partitionIntoBatches(List.of(krakow, warsaw, krakow2));
        assertThat(batches).hasSize(2);
        assertThat(batches.get(0)).containsExactly(krakow, krakow2);
        assertThat(batches.get(1)).containsExactly(warsaw);

        String ql = OverpassPoiClient.buildOverpassQuery(List.of(krakow, warsaw));
        assertThat(ql).contains("around:600,50.054000,19.935400");
        assertThat(ql).contains("around:2500,52.215100,21.035800");

        server.expect(requestTo("https://overpass.example/api/interpreter"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.USER_AGENT, "MeanderAtlas/0.1-test"))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "elements": [
                                    {
                                      "type": "way",
                                      "id": 12345,
                                      "center": { "lat": 50.05405, "lon": 19.93541 },
                                      "tags": {
                                        "name": "Zamek Królewski na Wawelu",
                                        "name:en": "Wawel Royal Castle",
                                        "historic": "castle",
                                        "addr:city": "Kraków",
                                        "opening_hours": "Mo-Su 09:00-17:00"
                                      }
                                    },
                                    {
                                      "type": "node",
                                      "id": 999,
                                      "lat": 50.0617,
                                      "lon": 19.9373,
                                      "tags": {
                                        "name": "Sukiennice",
                                        "tourism": "museum",
                                        "addr:town": "Kraków"
                                      }
                                    }
                                  ]
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        List<OpenPoiCandidate> candidates = client.fetchBatch(batches.get(0));
        server.verify();

        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).osmId()).isEqualTo("way/12345");
        assertThat(candidates.get(0).cityName()).isEqualTo("Kraków");
        assertThat(candidates.get(0).openingHoursRaw()).isEqualTo("Mo-Su 09:00-17:00");
        assertThat(candidates.get(1).osmId()).isEqualTo("node/999");
        assertThat(candidates.get(1).tags()).containsEntry("tourism", "museum");
    }

    @Test
    void throwsTransientExceptionOnTimeoutRemarkOrMalformedJsonAndPropagates429() {
        assertThatThrownBy(() ->
                        OverpassPoiClient.parseResponse(
                                "{\"elements\":[],\"remark\":\"runtime error: Query timed out\"}"))
                .isInstanceOf(OverpassTransientException.class);

        assertThatThrownBy(() -> OverpassPoiClient.parseResponse("{not-json"))
                .isInstanceOf(OverpassTransientException.class);

        assertThat(OverpassPoiClient.parseResponse("{\"elements\":[]}")).isEmpty();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OverpassPoiClient client = new OverpassPoiClient(builder, testProperties());
        server.expect(requestTo("https://overpass.example/api/interpreter"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        AnchoredEnrichmentTarget target =
                new AnchoredEnrichmentTarget(
                        PlaceId.random(),
                        "Wawel",
                        new GeoPoint(50.0540, 19.9354),
                        CoordSource.TAKEOUT,
                        CategorySource.HEURISTIC,
                        null,
                        null,
                        null,
                        null);

        assertThatThrownBy(() -> client.fetchBatch(List.of(target)))
                .isInstanceOf(HttpClientErrorException.TooManyRequests.class);
        server.verify();
    }
}
