package com.meanderatlas;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Tag("e2e")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiExportTest {

    private static final String PATHS_FIELD = "paths";
    private static final String TRAILING_NEWLINE = "\n";

    @LocalServerPort private int port;

    @Test
    void exportsDeterministicOpenApiContract() throws IOException {
        RestClient client =
                RestClient.builder().baseUrl(TestEnvironment.localhostUrl(port)).build();
        String rawSpec =
                client.get().uri(TestEnvironment.API_DOCS_PATH).retrieve().body(String.class);

        assertThat(rawSpec).isNotBlank();

        JsonMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        JsonNode root = mapper.readTree(rawSpec);
        JsonNode paths = root.path(PATHS_FIELD);
        assertThat(paths.has(TestEnvironment.ATLAS_HEALTH_PATH)).isTrue();
        assertThat(paths.has(TestEnvironment.ACTUATOR_LIVENESS_PATH)).isFalse();
        assertThat(paths.has(TestEnvironment.ACTUATOR_READINESS_PATH)).isFalse();

        String formatted = mapper.writeValueAsString(root) + TRAILING_NEWLINE;
        Path outputPath =
                Path.of(TestEnvironment.CONTRACTS_OPENAPI_PATH).toAbsolutePath().normalize();
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, formatted, StandardCharsets.UTF_8);
    }
}
