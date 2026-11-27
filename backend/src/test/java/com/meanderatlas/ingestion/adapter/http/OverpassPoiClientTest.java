package com.meanderatlas.ingestion.adapter.http;

import static com.meanderatlas.ingestion.OpenCatalogTestData.OVERPASS_URL;
import static com.meanderatlas.ingestion.OpenCatalogTestData.USER_AGENT;
import static com.meanderatlas.ingestion.OpenCatalogTestData.anchoredTarget;
import static com.meanderatlas.ingestion.OpenCatalogTestData.overpassNode;
import static com.meanderatlas.ingestion.OpenCatalogTestData.overpassResponse;
import static com.meanderatlas.ingestion.OpenCatalogTestData.overpassWay;
import static com.meanderatlas.ingestion.OpenCatalogTestData.properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meanderatlas.ingestion.adapter.http.OpenDataHttp.OpenDataResponseException;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.AnchoredEnrichmentTarget;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.OpenPoiCandidate;
import com.meanderatlas.shared.CoordSource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

class OverpassPoiClientTest {

    @Test
    void partitionsIntoSpatialCellsAndParsesNodesAndWayCenters() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OverpassPoiClient client = new OverpassPoiClient(builder, properties());

        AnchoredEnrichmentTarget krakow =
                anchoredTarget("Wawel", 50.0540, 19.9354, CoordSource.TAKEOUT, "PL");
        AnchoredEnrichmentTarget warsaw =
                anchoredTarget("Lazienki", 52.2151, 21.0358, CoordSource.TAKEOUT_S2_APPROX, "PL");
        AnchoredEnrichmentTarget krakow2 =
                anchoredTarget("Sukiennice", 50.0617, 19.9373, CoordSource.TAKEOUT, "PL");

        List<List<AnchoredEnrichmentTarget>> batches =
                client.partitionIntoBatches(List.of(krakow, warsaw, krakow2));
        assertThat(batches).hasSize(2);
        assertThat(batches.get(0)).containsExactly(krakow, krakow2);
        assertThat(batches.get(1)).containsExactly(warsaw);

        String ql = OverpassPoiClient.buildOverpassQuery(List.of(krakow, warsaw));
        assertThat(ql).contains("around:600,50.054000,19.935400");
        assertThat(ql).contains("around:2500,52.215100,21.035800");

        server.expect(requestTo(OVERPASS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
                .andRespond(
                        withSuccess(
                                overpassResponse(
                                        overpassWay(
                                                12345,
                                                50.05405,
                                                19.93541,
                                                Map.of(
                                                        "name", "Zamek Królewski na Wawelu",
                                                        "name:en", "Wawel Royal Castle",
                                                        "historic", "castle",
                                                        "addr:city", "Kraków",
                                                        "opening_hours", "Mo-Su 09:00-17:00")),
                                        overpassNode(
                                                999,
                                                50.0617,
                                                19.9373,
                                                Map.of(
                                                        "name", "Sukiennice",
                                                        "tourism", "museum",
                                                        "addr:town", "Kraków"))),
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
    void rejectsTimeoutRemarkAndMalformedJsonAndPropagatesHttpErrors() {
        assertThatThrownBy(() ->
                        OverpassPoiClient.parseResponse(
                                OpenDataHttp.readTree(
                                        "{\"elements\":[],\"remark\":\"runtime error: Query timed out\"}")))
                .isInstanceOf(OpenDataResponseException.class);
        assertThatThrownBy(() -> OpenDataHttp.readTree("{not-json"))
                .isInstanceOf(OpenDataResponseException.class);
        assertThat(OverpassPoiClient.parseResponse(OpenDataHttp.readTree(overpassResponse())))
                .isEmpty();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OverpassPoiClient client = new OverpassPoiClient(builder, properties());
        server.expect(requestTo(OVERPASS_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        AnchoredEnrichmentTarget target =
                anchoredTarget("Wawel", 50.0540, 19.9354, CoordSource.TAKEOUT, null);
        assertThatThrownBy(() -> client.fetchBatch(List.of(target)))
                .isInstanceOf(HttpClientErrorException.TooManyRequests.class);
        server.verify();
    }
}
