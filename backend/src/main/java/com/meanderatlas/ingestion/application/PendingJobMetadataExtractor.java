package com.meanderatlas.ingestion.application;

import com.meanderatlas.ingestion.domain.GoogleCid;
import com.meanderatlas.ingestion.domain.GoogleCidDecoder;
import com.meanderatlas.ingestion.domain.TextNormalizer;
import com.meanderatlas.ingestion.domain.source.EnrichmentJobRepository.PendingCoordinateJob;
import com.meanderatlas.ingestion.domain.source.ImportRecordKind;
import com.meanderatlas.ingestion.domain.takeout.TakeoutCsvParser;
import com.meanderatlas.shared.ListKind;
import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class PendingJobMetadataExtractor {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private PendingJobMetadataExtractor() {}

    static ImportedPlaceDraft extract(PendingCoordinateJob job) {
        JsonNode payload = parsePayload(job.payloadJson());
        if (job.recordType() == ImportRecordKind.REVIEW_GEOJSON) {
            String url = firstNonBlank(job.rawUrl(), readText(payload, "googleMapsUrl"));
            return ImportedPlaceDraft.review(
                    job.accountLabel(),
                    firstNonBlank(job.rawTitle(), readText(payload, "name")),
                    readText(payload, "reviewText"),
                    url,
                    resolveCid(job.googleCid(), url),
                    job.observedAt());
        }
        String url = firstNonBlank(job.rawUrl(), readText(payload, "url"));
        String listName =
                Objects.requireNonNullElse(
                        readText(payload, "listName"), ImportedPlaceDraft.LIST_NAME_SAVED_DEFAULT);
        return ImportedPlaceDraft.saved(
                job.accountLabel(),
                firstNonBlank(job.rawTitle(), readText(payload, "title")),
                url,
                resolveCid(job.googleCid(), url),
                listName,
                resolveListKind(readText(payload, "listKind"), listName),
                firstNonBlank(readText(payload, "note"), readText(payload, "comment")),
                readText(payload, "tags"),
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
        return Arrays.stream(ListKind.values())
                .filter(kind -> kind.name().equals(rawListKind))
                .findFirst()
                .orElseGet(() -> TakeoutCsvParser.classifyListKind(listName));
    }
}
