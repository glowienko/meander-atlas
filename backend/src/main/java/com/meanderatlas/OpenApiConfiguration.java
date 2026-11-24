package com.meanderatlas;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfiguration {

    private static final String API_TITLE = "MeanderAtlas API";
    private static final String API_VERSION = "v1";
    private static final String DEFAULT_SERVER_URL = "/";

    @Bean
    OpenAPI meanderAtlasOpenApi() {
        return new OpenAPI()
                .info(new Info().title(API_TITLE).version(API_VERSION))
                .servers(List.of(new Server().url(DEFAULT_SERVER_URL)));
    }
}
