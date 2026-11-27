package com.meanderatlas.ingestion.adapter.http;

import com.meanderatlas.ingestion.domain.TextNormalizer;
import com.meanderatlas.shared.GeoPoint;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

final class OpenDataHttp {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final RestClient restClient;
    private final String userAgent;

    OpenDataHttp(RestClient.Builder builder, String userAgent) {
        this.restClient = builder.build();
        this.userAgent = userAgent;
    }

    JsonNode get(URI uri) {
        return readTree(
                restClient
                        .get()
                        .uri(uri)
                        .header(HttpHeaders.USER_AGENT, userAgent)
                        .retrieve()
                        .body(String.class));
    }

    JsonNode postForm(String url, String formBody) {
        return readTree(
                restClient
                        .post()
                        .uri(url)
                        .header(HttpHeaders.USER_AGENT, userAgent)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(formBody)
                        .retrieve()
                        .body(String.class));
    }

    static JsonNode readTree(@Nullable String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return MissingNode.getInstance();
        }
        try {
            return JSON_MAPPER.readTree(rawResponse);
        } catch (JacksonException ex) {
            throw new OpenDataResponseException("Malformed JSON response", ex);
        }
    }

    static Optional<GeoPoint> validPoint(@Nullable JsonNode lat, @Nullable JsonNode lon) {
        if (lat == null || lon == null || !lat.isNumber() || !lon.isNumber()) {
            return Optional.empty();
        }
        double latitude = lat.doubleValue();
        double longitude = lon.doubleValue();
        boolean inRange =
                latitude >= GeoPoint.MIN_LATITUDE
                        && latitude <= GeoPoint.MAX_LATITUDE
                        && longitude >= GeoPoint.MIN_LONGITUDE
                        && longitude <= GeoPoint.MAX_LONGITUDE;
        return inRange ? Optional.of(new GeoPoint(latitude, longitude)) : Optional.empty();
    }

    static @Nullable String firstNonBlank(@Nullable String... candidates) {
        return Stream.of(candidates)
                .map(TextNormalizer::trimToNull)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    static String formatCoordinate(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    static final class OpenDataResponseException extends RuntimeException {

        OpenDataResponseException(String message) {
            super(message);
        }

        OpenDataResponseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
