package com.meanderatlas;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfiguration {

    @Bean
    OpenAPI meanderAtlasOpenApi() {
        return new OpenAPI()
                .info(new Info().title("MeanderAtlas API").version("v1"))
                .servers(List.of(new Server().url("http://localhost:8080")));
    }
}
