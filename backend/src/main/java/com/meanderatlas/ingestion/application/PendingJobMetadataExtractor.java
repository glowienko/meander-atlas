package com.meanderatlas.ingestion.application;

import com.meanderatlas.ingestion.domain.GoogleCid;
import com.meanderatlas.ingestion.domain.GoogleCidDecoder;
import com.meanderatlas.ingestion.domain.TextNormalizer;
import com.meanderatlas.ingestion.domain.TitleCoordinateParser;
import com.meanderatlas.ingestion.domain.classification.PlaceCategoryClassifier;
import com.meanderatlas.ingestion.domain.classification.PlaceClassification;
import com.meanderatlas.ingestion.domain.source.EnrichmentJobRepository.PendingCoordinateJob;
import com.meanderatlas.ingestion.domain.source.ImportRecordKind;
import com.meanderatlas.ingestion.domain.takeout.TakeoutCsvParser;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class PendingJobMetadataExtractor {

    private static final String DEFAULT_LIST_NAME = "Saved Places";
    private static final String LIST_NAME_REVIEWS = "Google Reviews";
    private static final String FALLBACK_REVIEW_NAME = "Reviewed place";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private PendingJobMetadataExtractor() {}

    static ExtractedJobMetadata extract(PendingCoordinateJob job) {
        JsonNode payload = parsePayload(job.payloadJson());
        if (job.recordType() == ImportRecordKind.REVIEW_GEOJSON) {
            String rawName = firstNonBlank(job.rawTitle(), readText(payload, "name"));
            String canonicalName = rawName == null ? FALLBACK_REVIEW_NAME : rawName;
            String url = firstNonBlank(job.rawUrl(), readText(payload, "googleMapsUrl"));
            String reviewText = readText(payload, "reviewText");
            Long cid = resolveCid(job.googleCid(), url);
            PlaceClassification classification =
                    PlaceCategoryClassifier.classify(canonicalName, reviewText, null);
            Optional<GeoPoint> titleCoords = TitleCoordinateParser.parse(rawName);
            return new ExtractedJobMetadata(
                    rawName,
                    canonicalName,
                    url,
                    cid,
                    LIST_NAME_REVIEWS,
                    ListKind.REVIEW,
                    reviewText,
                    null,
                    classification,
                    titleCoords.orElse(null),
                    job.observedAt(),
                    VisitStatus.VISITED,
                    VisitSource.REVIEW,
                    job.observedAt());
        }
        String title = firstNonBlank(job.rawTitle(), readText(payload, "title"));
        String url = firstNonBlank(job.rawUrl(), readText(payload, "url"));
        Long cid = resolveCid(job.googleCid(), url);
        String listName =
                Objects.requireNonNullElse(readText(payload, "listName"), DEFAULT_LIST_NAME);
        ListKind listKind = resolveListKind(readText(payload, "listKind"), listName);
        String note = firstNonBlank(readText(payload, "note"), readText(payload, "comment"));
        String tags = readText(payload, "tags");
        String canonicalTitle = title == null ? DEFAULT_LIST_NAME : title;
        PlaceClassification classification =
                PlaceCategoryClassifier.classify(canonicalTitle, note, tags);
        Optional<GeoPoint> titleCoords = TitleCoordinateParser.parse(title);
        return new ExtractedJobMetadata(
                title,
                canonicalTitle,
                url,
                cid,
                listName,
                listKind,
                note,
                tags,
                classification,
                titleCoords.orElse(null),
                null,
                null,
                null,
                null);
    }

    private static JsonNode parsePayload(String payloadJson) {
        try {
            return JSON_MAPPER.readTree(payloadJson);
        } catch (JacksonException ignored) {
            return JSON_MAPPER.createObjectNode();
        }
    }

    private static @Nullable String readText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        return TextNormalizer.trimToNull(field.asString());
    }

    private static @Nullable String firstNonBlank(
            @Nullable String primary, @Nullable String fallback) {
        String normalizedPrimary = TextNormalizer.trimToNull(primary);
        return normalizedPrimary != null ? normalizedPrimary : TextNormalizer.trimToNull(fallback);
    }

    private static @Nullable Long resolveCid(@Nullable Long existingCid, @Nullable String url) {
        if (existingCid != null) {
            return existingCid;
        }
        return GoogleCidDecoder.decodeFromUrl(url).map(GoogleCid::unsignedBits).orElse(null);
    }

    private static ListKind resolveListKind(@Nullable String rawListKind, String listName) {
        if (rawListKind != null) {
            try {
                return ListKind.valueOf(rawListKind);
            } catch (IllegalArgumentException ignored) {
                return TakeoutCsvParser.classifyListKind(listName);
            }
        }
        return TakeoutCsvParser.classifyListKind(listName);
    }

    record ExtractedJobMetadata(
            @Nullable String rawTitle,
            String canonicalTitle,
            @Nullable String url,
            @Nullable Long cid,
            String listName,
            ListKind listKind,
            @Nullable String note,
            @Nullable String tags,
            PlaceClassification classification,
            @Nullable GeoPoint titleCoords,
            @Nullable Instant savedAt,
            @Nullable VisitStatus visitStatus,
            @Nullable VisitSource visitSource,
            @Nullable Instant visitedAt) {}
}
