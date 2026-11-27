package com.meanderatlas.ingestion.adapter.http;

import com.meanderatlas.ingestion.application.EnrichmentProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EnrichmentProperties.class)
class EnrichmentHttpConfiguration {

    @Bean
    OverpassPoiClient overpassPoiClient(EnrichmentProperties properties) {
        return new OverpassPoiClient(buildRestClientBuilder(properties), properties);
    }

    @Bean
    PhotonPoiClient photonPoiClient(EnrichmentProperties properties) {
        return new PhotonPoiClient(buildRestClientBuilder(properties), properties);
    }

    @Bean
    WikimediaPhotoClient wikimediaPhotoClient(EnrichmentProperties properties) {
        return new WikimediaPhotoClient(buildRestClientBuilder(properties), properties);
    }

    private static RestClient.Builder buildRestClientBuilder(EnrichmentProperties properties) {
        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
        return RestClient.builder().requestFactory(requestFactory);
    }
}
