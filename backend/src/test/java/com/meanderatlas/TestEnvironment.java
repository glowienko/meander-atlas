package com.meanderatlas;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

public final class TestEnvironment {

    public static final String POSTGRES_COMPATIBLE_IMAGE = "postgres";
    public static final String LOCALHOST_URL_PREFIX = "http://localhost:";
    public static final String ATLAS_HEALTH_PATH = "/api/v1/atlas/health";
    public static final String API_DOCS_PATH = "/v3/api-docs";
    public static final String ACTUATOR_LIVENESS_PATH = "/actuator/health/liveness";
    public static final String ACTUATOR_READINESS_PATH = "/actuator/health/readiness";
    public static final String CONTRACTS_OPENAPI_PATH = "../contracts/openapi.json";

    private static final String PROPERTIES_RESOURCE = "/test-environment.properties";
    private static final String POSTGIS_IMAGE_KEY = "postgis.image";
    private static final String POSTGIS_MAJOR_PREFIX_KEY = "postgis.major.prefix";
    private static final Properties PROPERTIES = loadProperties();

    public static final String POSTGIS_IMAGE = requiredProperty(POSTGIS_IMAGE_KEY);
    public static final String POSTGIS_MAJOR_PREFIX = requiredProperty(POSTGIS_MAJOR_PREFIX_KEY);

    private TestEnvironment() {}

    public static String localhostUrl(int port) {
        return LOCALHOST_URL_PREFIX + port;
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream stream = TestEnvironment.class.getResourceAsStream(PROPERTIES_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Missing test environment resource: " + PROPERTIES_RESOURCE);
            }
            properties.load(stream);
            return properties;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to load " + PROPERTIES_RESOURCE, ex);
        }
    }

    private static String requiredProperty(String key) {
        String value = PROPERTIES.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required test environment property: " + key);
        }
        return value;
    }
}
