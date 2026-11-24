package com.meanderatlas.atlas;

import static org.assertj.core.api.Assertions.assertThat;

import com.meanderatlas.TestEnvironment;
import com.meanderatlas.TestcontainersConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Tag("e2e")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AtlasHealthE2eTest {

    private static final String STATUS_FIELD = "status";

    @LocalServerPort private int port;

    @Test
    void returnsUpAndPostgisVersionOverHttp() {
        RestClient client =
                RestClient.builder().baseUrl(TestEnvironment.localhostUrl(port)).build();

        ResponseEntity<AtlasHealthView> response =
                client.get()
                        .uri(TestEnvironment.ATLAS_HEALTH_PATH)
                        .retrieve()
                        .toEntity(AtlasHealthView.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AtlasHealthView body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(AtlasHealthView.STATUS_UP);
        assertThat(body.database()).isEqualTo(AtlasHealthView.DATABASE_POSTGRESQL);
        assertThat(body.postgisVersion()).startsWith(TestEnvironment.POSTGIS_MAJOR_PREFIX);
    }

    @Test
    void exposesActuatorLivenessAndReadinessProbes() {
        RestClient client =
                RestClient.builder().baseUrl(TestEnvironment.localhostUrl(port)).build();

        ResponseEntity<JsonNode> liveness =
                client.get()
                        .uri(TestEnvironment.ACTUATOR_LIVENESS_PATH)
                        .retrieve()
                        .toEntity(JsonNode.class);
        ResponseEntity<JsonNode> readiness =
                client.get()
                        .uri(TestEnvironment.ACTUATOR_READINESS_PATH)
                        .retrieve()
                        .toEntity(JsonNode.class);

        assertThat(liveness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(liveness.getBody()).isNotNull();
        assertThat(liveness.getBody().path(STATUS_FIELD).asString())
                .isEqualTo(AtlasHealthView.STATUS_UP);

        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readiness.getBody()).isNotNull();
        assertThat(readiness.getBody().path(STATUS_FIELD).asString())
                .isEqualTo(AtlasHealthView.STATUS_UP);
    }
}
