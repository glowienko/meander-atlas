package com.meanderatlas.ingestion.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.enrichment")
public record EnrichmentProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("25") int batchSize,
        @DefaultValue("3") int maxRetries,
        @DefaultValue("5000") long connectTimeoutMs,
        @DefaultValue("30000") long readTimeoutMs,
        @DefaultValue("https://overpass-api.de/api/interpreter") String overpassUrl,
        @DefaultValue("https://photon.komoot.io/api") String photonUrl,
        @DefaultValue("https://commons.wikimedia.org/w/api.php") String wikimediaCommonsApiUrl,
        @DefaultValue("https://{lang}.wikipedia.org/api/rest_v1") String wikipediaRestUrlTemplate,
        @DefaultValue("https://www.wikidata.org/w/api.php") String wikidataApiUrl,
        @DefaultValue("MeanderAtlas/0.1 (local-personal-atlas)") String wikimediaUserAgent) {}
