package com.meanderatlas.atlas;

import static org.assertj.core.api.Assertions.assertThat;

import com.meanderatlas.TestcontainersConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@Tag("e2e")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AtlasHealthE2eTest {

    @LocalServerPort private int port;

    @Test
    void returnsUpAndPostgisVersionOverHttp() {
        RestClient client = RestClient.builder().baseUrl("http://localhost:" + port).build();

        ResponseEntity<AtlasHealthView> response =
                client.get().uri("/api/v1/atlas/health").retrieve().toEntity(AtlasHealthView.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AtlasHealthView body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo("UP");
        assertThat(body.database()).isEqualTo("postgresql");
        assertThat(body.postgisVersion()).startsWith("3.");
    }
}
