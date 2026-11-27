package com.meanderatlas.ingestion.adapter.http;

import com.meanderatlas.ingestion.domain.TextNormalizer;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.PhotoLookupResult;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class WikimediaPhotoClient {

    private static final String UPLOAD_WIKIMEDIA_PREFIX = "https://upload.wikimedia.org/";
    private static final int THUMB_WIDTH_PX = 640;
    private static final Pattern WIKIPEDIA_LANG_PATTERN =
            Pattern.compile("^[a-z]{2,12}(?:-[a-z]+)?$");
    private static final Pattern WIKIDATA_QID_PATTERN = Pattern.compile("^Q[0-9]+$");
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final RestClient restClient;
    private final EnrichmentProperties properties;

    WikimediaPhotoClient(RestClient.Builder builder, EnrichmentProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    PhotoLookupResult resolve(Map<String, String> tags) {
        if (tags.isEmpty()) {
            return new PhotoLookupResult(null, null);
        }
        return resolveFromCommons(tags.get("wikimedia_commons"))
                .or(() -> resolveFromWikipedia(tags.get("wikipedia")))
                .or(() -> resolveFromWikidata(tags.get("wikidata")))
                .or(() -> resolveFromImageTag(tags.get("image")))
                .orElse(new PhotoLookupResult(null, null));
    }

    static boolean isSafeWikimediaUrl(@Nullable String url) {
        return url != null
                && !url.isBlank()
                && !url.contains(" ")
                && url.startsWith(UPLOAD_WIKIMEDIA_PREFIX);
    }

    private Optional<PhotoLookupResult> resolveFromCommons(@Nullable String rawCommons) {
        String title = TextNormalizer.trimToNull(rawCommons);
        if (title == null) {
            return Optional.empty();
        }
        if (title.toLowerCase(Locale.ROOT).startsWith("category:")) {
            return Optional.empty();
        }
        String normalizedTitle =
                title.toLowerCase(Locale.ROOT).startsWith("file:") ? title : "File:" + title;
        URI uri =
                UriComponentsBuilder.fromUriString(properties.wikimediaCommonsApiUrl())
                        .queryParam("action", "query")
                        .queryParam("titles", normalizedTitle)
                        .queryParam("prop", "imageinfo")
                        .queryParam("iiprop", "url")
                        .queryParam("iiurlwidth", THUMB_WIDTH_PX)
                        .queryParam("format", "json")
                        .queryParam("formatversion", "2")
                        .build()
                        .encode()
                        .toUri();
        return fetchAndParseCommonsImageInfo(uri, "wikimedia_commons");
    }

    private Optional<PhotoLookupResult> resolveFromWikipedia(@Nullable String rawWikipedia) {
        String trimmed = TextNormalizer.trimToNull(rawWikipedia);
        if (trimmed == null) {
            return Optional.empty();
        }
        int colon = trimmed.indexOf(':');
        if (colon <= 0 || colon >= trimmed.length() - 1) {
            return Optional.empty();
        }
        String lang = trimmed.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        String pageTitle = trimmed.substring(colon + 1).trim().replace(' ', '_');
        if (!WIKIPEDIA_LANG_PATTERN.matcher(lang).matches() || pageTitle.isEmpty()) {
            return Optional.empty();
        }
        String baseTemplate = properties.wikipediaRestUrlTemplate().replace("{lang}", lang);
        String normalizedBase =
                baseTemplate.endsWith("/")
                        ? baseTemplate.substring(0, baseTemplate.length() - 1)
                        : baseTemplate;
        String encodedTitle = UriUtils.encodePathSegment(pageTitle, StandardCharsets.UTF_8);
        URI uri = URI.create(normalizedBase + "/page/summary/" + encodedTitle);
        String body = safeGet(uri);
        if (body == null) {
            return Optional.empty();
        }
        try {
            JsonNode root = JSON_MAPPER.readTree(body);
            String candidate =
                    firstSafeUrl(
                            root.path("thumbnail").path("source").asString(""),
                            root.path("originalimage").path("source").asString(""));
            return candidate == null
                    ? Optional.empty()
                    : Optional.of(new PhotoLookupResult(candidate, "wikipedia"));
        } catch (JacksonException ex) {
            return Optional.empty();
        }
    }

    private Optional<PhotoLookupResult> resolveFromWikidata(@Nullable String rawWikidata) {
        String qid = TextNormalizer.trimToNull(rawWikidata);
        if (qid == null || !WIKIDATA_QID_PATTERN.matcher(qid).matches()) {
            return Optional.empty();
        }
        URI uri =
                UriComponentsBuilder.fromUriString(properties.wikidataApiUrl())
                        .queryParam("action", "wbgetentities")
                        .queryParam("ids", qid)
                        .queryParam("props", "claims")
                        .queryParam("format", "json")
                        .build()
                        .encode()
                        .toUri();
        String body = safeGet(uri);
        if (body == null) {
            return Optional.empty();
        }
        try {
            JsonNode root = JSON_MAPPER.readTree(body);
            JsonNode p18 = root.path("entities").path(qid).path("claims").path("P18");
            if (!p18.isArray() || p18.isEmpty()) {
                return Optional.empty();
            }
            String filename =
                    TextNormalizer.trimToNull(
                            p18.get(0)
                                    .path("mainsnak")
                                    .path("datavalue")
                                    .path("value")
                                    .asString(""));
            if (filename == null) {
                return Optional.empty();
            }
            return resolveFromCommons("File:" + filename)
                    .map(result -> new PhotoLookupResult(result.photoUrl(), "wikidata"));
        } catch (JacksonException ex) {
            return Optional.empty();
        }
    }

    private Optional<PhotoLookupResult> resolveFromImageTag(@Nullable String rawImage) {
        String trimmed = TextNormalizer.trimToNull(rawImage);
        if (trimmed == null) {
            return Optional.empty();
        }
        if (isSafeWikimediaUrl(trimmed)) {
            return Optional.of(new PhotoLookupResult(trimmed, "osm_image"));
        }
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("file:")) {
            return resolveFromCommons(trimmed);
        }
        return Optional.empty();
    }

    private Optional<PhotoLookupResult> fetchAndParseCommonsImageInfo(URI uri, String source) {
        String body = safeGet(uri);
        if (body == null) {
            return Optional.empty();
        }
        try {
            JsonNode pages = JSON_MAPPER.readTree(body).path("query").path("pages");
            if (pages.isArray()) {
                return StreamSupport.stream(pages.spliterator(), false)
                        .map(WikimediaPhotoClient::extractImageInfoUrl)
                        .flatMap(Optional::stream)
                        .findFirst()
                        .map(url -> new PhotoLookupResult(url, source));
            }
            if (pages.isObject()) {
                return pages.properties().stream()
                        .map(Map.Entry::getValue)
                        .map(WikimediaPhotoClient::extractImageInfoUrl)
                        .flatMap(Optional::stream)
                        .findFirst()
                        .map(url -> new PhotoLookupResult(url, source));
            }
            return Optional.empty();
        } catch (JacksonException ex) {
            return Optional.empty();
        }
    }

    private static Optional<String> extractImageInfoUrl(JsonNode pageNode) {
        JsonNode imageInfo = pageNode.path("imageinfo");
        if (!imageInfo.isArray() || imageInfo.isEmpty()) {
            return Optional.empty();
        }
        JsonNode first = imageInfo.get(0);
        String candidate =
                firstSafeUrl(first.path("thumburl").asString(""), first.path("url").asString(""));
        return Optional.ofNullable(candidate);
    }

    private static @Nullable String firstSafeUrl(String primary, String secondary) {
        if (isSafeWikimediaUrl(primary)) {
            return primary;
        }
        return isSafeWikimediaUrl(secondary) ? secondary : null;
    }

    private @Nullable String safeGet(URI uri) {
        try {
            return restClient
                    .get()
                    .uri(uri)
                    .header(HttpHeaders.USER_AGENT, properties.wikimediaUserAgent())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException ex) {
            return null;
        }
    }
}
