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

    @LocalServerPort private int port;

    @Test
    void exportsDeterministicOpenApiContract() throws IOException {
        RestClient client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        String rawSpec = client.get().uri("/v3/api-docs").retrieve().body(String.class);

        assertThat(rawSpec).isNotBlank();

        JsonMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        JsonNode root = mapper.readTree(rawSpec);
        assertThat(root.path("paths").has("/api/v1/atlas/health")).isTrue();

        String formatted = mapper.writeValueAsString(root) + "\n";
        Path outputPath = Path.of("../contracts/openapi.json").toAbsolutePath().normalize();
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, formatted, StandardCharsets.UTF_8);
    }
}
