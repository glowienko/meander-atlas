package com.meanderatlas.atlas.domain.boundaries;

import com.meanderatlas.atlas.domain.RegionLevel;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class NaturalEarthGeoJsonParser {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final String INVALID_CODE_SENTINEL = "-99";
    private static final String TILDE = "~";
    private static final String HYPHEN = "-";
    private static final String EMPTY_CODE = "";
    private static final Set<String> POLYGON_TYPES = Set.of("Polygon", "MultiPolygon");
    private static final List<String> COUNTRY_CODE_KEYS = List.of("ISO_A2_EH", "ISO_A2", "ADM0_A3");
    private static final List<String> COUNTRY_NAME_KEYS = List.of("ADMIN", "NAME", "NAME_LONG");
    private static final List<String> ADMIN1_COUNTRY_KEYS = List.of("iso_a2", "adm0_a3");
    private static final List<String> ADMIN1_NAME_KEYS = List.of("name", "name_en", "gn_name");

    private NaturalEarthGeoJsonParser() {}

    public static List<BoundaryFeature> parseCountries(Path geoJsonFile) throws IOException {
        Objects.requireNonNull(geoJsonFile, "geoJsonFile must not be null");
        try (Reader reader = Files.newBufferedReader(geoJsonFile, StandardCharsets.UTF_8)) {
            return parseCountries(reader);
        }
    }

    public static List<BoundaryFeature> parseAdmin1(Path geoJsonFile) throws IOException {
        Objects.requireNonNull(geoJsonFile, "geoJsonFile must not be null");
        try (Reader reader = Files.newBufferedReader(geoJsonFile, StandardCharsets.UTF_8)) {
            return parseAdmin1(reader);
        }
    }

    private static List<BoundaryFeature> parseCountries(Reader reader) {
        List<BoundaryFeature> rawFeatures =
                withFeatureStream(
                        reader,
                        features ->
                                features.map(NaturalEarthGeoJsonParser::toCountryFeature)
                                        .flatMap(Optional::stream)
                                        .toList());
        return deduplicatePreservingOrder(rawFeatures);
    }

    private static List<BoundaryFeature> parseAdmin1(Reader reader) {
        List<RawAdmin1> rawAdmin1 =
                withFeatureStream(
                        reader,
                        features ->
                                features.map(NaturalEarthGeoJsonParser::toRawAdmin1)
                                        .flatMap(Optional::stream)
                                        .toList());
        Map<String, Long> isoCounts =
                rawAdmin1.stream()
                        .map(RawAdmin1::iso3166_2)
                        .filter(Objects::nonNull)
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        Function.identity(), _ -> 1L, Long::sum));
        List<BoundaryFeature> resolved =
                rawAdmin1.stream()
                        .map(raw -> resolveAdmin1(raw, isoCounts))
                        .flatMap(Optional::stream)
                        .toList();
        return deduplicatePreservingOrder(resolved);
    }

    private static Optional<BoundaryFeature> toCountryFeature(JsonNode feature) {
        JsonNode geometry = feature.path("geometry");
        if (!isPolygonGeometry(geometry)) {
            return Optional.empty();
        }
        JsonNode properties = feature.path("properties");
        String code = firstCode(properties, COUNTRY_CODE_KEYS);
        String name = firstText(properties, COUNTRY_NAME_KEYS);
        if (code == null || name == null) {
            return Optional.empty();
        }
        return Optional.of(
                new BoundaryFeature(
                        RegionLevel.COUNTRY,
                        code,
                        code,
                        name,
                        JSON_MAPPER.writeValueAsString(geometry)));
    }

    private static Optional<RawAdmin1> toRawAdmin1(JsonNode feature) {
        JsonNode geometry = feature.path("geometry");
        if (!isPolygonGeometry(geometry)) {
            return Optional.empty();
        }
        JsonNode properties = feature.path("properties");
        String iso3166_2 = normalizeValidIso3166(findProperty(properties, "iso_3166_2"));
        String adm1Code = normalizeValidCode(findProperty(properties, "adm1_code"));
        String name = firstText(properties, ADMIN1_NAME_KEYS);
        if (name == null || (iso3166_2 == null && adm1Code == null)) {
            return Optional.empty();
        }
        String countryCode =
                Optional.ofNullable(firstCode(properties, ADMIN1_COUNTRY_KEYS))
                        .orElseGet(() -> deriveCountryCodeFromIso(iso3166_2));
        if (countryCode == null) {
            return Optional.empty();
        }
        return Optional.of(
                new RawAdmin1(
                        iso3166_2,
                        adm1Code,
                        countryCode,
                        name,
                        JSON_MAPPER.writeValueAsString(geometry)));
    }

    private static Optional<BoundaryFeature> resolveAdmin1(
            RawAdmin1 raw, Map<String, Long> isoCounts) {
        String iso = raw.iso3166_2();
        boolean useIso = iso != null && isoCounts.getOrDefault(iso, 0L) == 1L;
        String code = useIso ? iso : raw.adm1Code();
        if (code == null) {
            return Optional.empty();
        }
        return Optional.of(
                new BoundaryFeature(
                        RegionLevel.ADMIN1,
                        code,
                        raw.countryCode(),
                        raw.name(),
                        raw.geometryJson()));
    }

    private static List<BoundaryFeature> deduplicatePreservingOrder(
            List<BoundaryFeature> features) {
        Map<String, Integer> firstIndexByCode =
                IntStream.range(0, features.size())
                        .boxed()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        i -> features.get(i).code(),
                                        Function.identity(),
                                        Math::min));
        return IntStream.range(0, features.size())
                .filter(i -> Objects.equals(firstIndexByCode.get(features.get(i).code()), i))
                .mapToObj(features::get)
                .toList();
    }

    private static boolean isPolygonGeometry(JsonNode geometry) {
        if (!geometry.isObject()) {
            return false;
        }
        String type = geometry.path("type").asString("");
        JsonNode coordinates = geometry.path("coordinates");
        return POLYGON_TYPES.contains(type) && coordinates.isArray() && !coordinates.isEmpty();
    }

    private static @Nullable String firstCode(JsonNode properties, List<String> keys) {
        return keys.stream()
                .map(key -> normalizeValidCode(findProperty(properties, key)))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static @Nullable String firstText(JsonNode properties, List<String> keys) {
        return keys.stream()
                .map(key -> trimToNull(findProperty(properties, key)))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static @Nullable String findProperty(JsonNode properties, String expectedKey) {
        if (!properties.isObject()) {
            return null;
        }
        JsonNode exact = properties.get(expectedKey);
        if (exact != null && !exact.isNull()) {
            return exact.asString("");
        }
        String lowerExpected = expectedKey.toLowerCase(Locale.ROOT);
        return properties.properties().stream()
                .filter(entry -> entry.getKey().toLowerCase(Locale.ROOT).equals(lowerExpected))
                .map(Map.Entry::getValue)
                .filter(node -> !node.isNull())
                .map(node -> node.asString(""))
                .findFirst()
                .orElse(null);
    }

    private static @Nullable String normalizeValidIso3166(@Nullable String raw) {
        String code = normalizeValidCode(raw);
        if (code == null || code.contains(TILDE)) {
            return null;
        }
        return code;
    }

    private static @Nullable String normalizeValidCode(@Nullable String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null || INVALID_CODE_SENTINEL.equals(trimmed)) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private static @Nullable String deriveCountryCodeFromIso(@Nullable String iso3166_2) {
        if (iso3166_2 == null) {
            return null;
        }
        int dashIndex = iso3166_2.indexOf(HYPHEN);
        if (dashIndex <= 0) {
            return null;
        }
        return normalizeValidCode(iso3166_2.substring(0, dashIndex));
    }

    private static @Nullable String trimToNull(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static <T> T withFeatureStream(Reader reader, Function<Stream<JsonNode>, T> fn) {
        try (JsonParser parser = JSON_MAPPER.createParser(reader)) {
            advanceToFeaturesArray(parser);
            Spliterator<JsonNode> spliterator =
                    new Spliterators.AbstractSpliterator<>(
                            Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL) {
                        @Override
                        public boolean tryAdvance(Consumer<? super JsonNode> action) {
                            JsonToken token = parser.nextToken();
                            while (token != null && token != JsonToken.END_ARRAY) {
                                if (token == JsonToken.START_OBJECT) {
                                    JsonNode node = parser.readValueAsTree();
                                    action.accept(node);
                                    return true;
                                }
                                if (token == JsonToken.START_ARRAY) {
                                    parser.skipChildren();
                                }
                                token = parser.nextToken();
                            }
                            return false;
                        }
                    };
            try (Stream<JsonNode> stream = StreamSupport.stream(spliterator, false)) {
                return fn.apply(stream);
            }
        }
    }

    private static void advanceToFeaturesArray(JsonParser parser) {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            return;
        }
        JsonToken token = parser.nextToken();
        while (token != null && token != JsonToken.END_OBJECT) {
            if (token == JsonToken.PROPERTY_NAME) {
                String fieldName = parser.currentName();
                JsonToken valueToken = parser.nextToken();
                if ("features".equals(fieldName) && valueToken == JsonToken.START_ARRAY) {
                    return;
                }
                if (valueToken == JsonToken.START_OBJECT || valueToken == JsonToken.START_ARRAY) {
                    parser.skipChildren();
                }
            }
            token = parser.nextToken();
        }
    }

    private record RawAdmin1(
            @Nullable String iso3166_2,
            @Nullable String adm1Code,
            String countryCode,
            String name,
            String geometryJson) {

        private RawAdmin1 {
            Objects.requireNonNull(countryCode, EMPTY_CODE);
        }
    }
}
