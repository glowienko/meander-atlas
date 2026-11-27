package com.meanderatlas.ingestion.adapter.http;

import com.meanderatlas.ingestion.application.EnrichmentProperties;
import com.meanderatlas.ingestion.domain.TextNormalizer;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.PhotoLookupResult;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;

class WikimediaPhotoClient {

    private static final String UPLOAD_WIKIMEDIA_PREFIX = "https://upload.wikimedia.org/";
    private static final int THUMB_WIDTH_PX = 640;
    private static final Pattern WIKIPEDIA_LANG_PATTERN =
            Pattern.compile("^[a-z]{2,12}(?:-[a-z]+)?$");
    private static final Pattern WIKIDATA_QID_PATTERN = Pattern.compile("^Q[0-9]+$");

    private final OpenDataHttp http;
    private final EnrichmentProperties properties;

    WikimediaPhotoClient(RestClient.Builder builder, EnrichmentProperties properties) {
        this.http = new OpenDataHttp(builder, properties.wikimediaUserAgent());
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
        return parseCommonsImageInfo(http.get(uri), "wikimedia_commons");
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
        JsonNode root = http.get(uri);
        return Optional.ofNullable(
                        firstSafeUrl(
                                root.path("thumbnail").path("source").asString(""),
                                root.path("originalimage").path("source").asString("")))
                .map(url -> new PhotoLookupResult(url, "wikipedia"));
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
        String filename =
                TextNormalizer.trimToNull(
                        http.get(uri)
                                .path("entities")
                                .path(qid)
                                .path("claims")
                                .path("P18")
                                .path(0)
                                .path("mainsnak")
                                .path("datavalue")
                                .path("value")
                                .asString(""));
        return Optional.ofNullable(filename)
                .flatMap(name -> resolveFromCommons("File:" + name))
                .map(result -> new PhotoLookupResult(result.photoUrl(), "wikidata"));
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

    private static Optional<PhotoLookupResult> parseCommonsImageInfo(JsonNode root, String source) {
        JsonNode pages = root.path("query").path("pages");
        Stream<JsonNode> pageNodes =
                pages.isArray()
                        ? StreamSupport.stream(pages.spliterator(), false)
                        : pages.properties().stream().map(Map.Entry::getValue);
        return pageNodes
                .map(WikimediaPhotoClient::extractImageInfoUrl)
                .flatMap(Optional::stream)
                .findFirst()
                .map(url -> new PhotoLookupResult(url, source));
    }

    private static Optional<String> extractImageInfoUrl(JsonNode pageNode) {
        JsonNode first = pageNode.path("imageinfo").path(0);
        return Optional.ofNullable(
                firstSafeUrl(first.path("thumburl").asString(""), first.path("url").asString("")));
    }

    private static @Nullable String firstSafeUrl(String primary, String secondary) {
        if (isSafeWikimediaUrl(primary)) {
            return primary;
        }
        return isSafeWikimediaUrl(secondary) ? secondary : null;
    }
}
