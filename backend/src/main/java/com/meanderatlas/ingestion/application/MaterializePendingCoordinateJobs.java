package com.meanderatlas.ingestion.application;

import com.meanderatlas.atlas.AtlasFacade;
import com.meanderatlas.atlas.AttachCsvMembershipCommand;
import com.meanderatlas.atlas.ImportedPlaceUpsertCommand;
import com.meanderatlas.atlas.UpsertedPlaceView;
import com.meanderatlas.ingestion.OfflineMaterializationSummaryView;
import com.meanderatlas.ingestion.domain.GoogleCid;
import com.meanderatlas.ingestion.domain.GoogleCidDecoder;
import com.meanderatlas.ingestion.domain.GoogleS2CellDecoder;
import com.meanderatlas.ingestion.domain.TextNormalizer;
import com.meanderatlas.ingestion.domain.TitleCoordinateParser;
import com.meanderatlas.ingestion.domain.classification.PlaceCategoryClassifier;
import com.meanderatlas.ingestion.domain.classification.PlaceClassification;
import com.meanderatlas.ingestion.domain.source.EnrichmentJobRepository;
import com.meanderatlas.ingestion.domain.source.EnrichmentJobRepository.PendingCoordinateJob;
import com.meanderatlas.ingestion.domain.source.ImportRecordKind;
import com.meanderatlas.ingestion.domain.takeout.TakeoutCsvParser;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class MaterializePendingCoordinateJobs {

    private static final int BATCH_SIZE = 200;
    private static final String DEFAULT_LIST_NAME = "Saved Places";
    private static final String LIST_NAME_REVIEWS = "Google Reviews";
    private static final String FALLBACK_REVIEW_NAME = "Reviewed place";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final EnrichmentJobRepository enrichmentJobRepository;
    private final AtlasFacade atlasFacade;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionOperations transactionOperations;

    public MaterializePendingCoordinateJobs(
            EnrichmentJobRepository enrichmentJobRepository,
            AtlasFacade atlasFacade,
            CurrentUserProvider currentUserProvider,
            TransactionOperations transactionOperations) {
        this.enrichmentJobRepository = enrichmentJobRepository;
        this.atlasFacade = atlasFacade;
        this.currentUserProvider = currentUserProvider;
        this.transactionOperations = transactionOperations;
    }

    public OfflineMaterializationSummaryView execute() {
        UserId userId = currentUserProvider.currentUserId();
        BatchTotals totals = drainPendingCoordinateJobs(userId);
        FinalQueueCounts finalCounts =
                Objects.requireNonNull(
                        transactionOperations.execute(_ -> {
                            List<PlaceId> needingEnrichment =
                                    atlasFacade.findPlaceIdsNeedingEnrichment();
                            int enqueuedCount =
                                    enrichmentJobRepository.enqueueOpenCatalogEnrichments(
                                            userId, needingEnrichment);
                            int remainingCount =
                                    enrichmentJobRepository.countPendingCoordinateLookups(userId);
                            return new FinalQueueCounts(enqueuedCount, remainingCount);
                        }));
        int materializedCount =
                totals.exactFromTitleCount()
                        + totals.approxFromS2Count()
                        + totals.mergedByCidCount();
        return new OfflineMaterializationSummaryView(
                materializedCount,
                totals.exactFromTitleCount(),
                totals.approxFromS2Count(),
                totals.mergedByCidCount(),
                finalCounts.remainingLookupCount(),
                finalCounts.enqueuedEnrichmentCount());
    }

    private BatchTotals drainPendingCoordinateJobs(UserId userId) {
        UUID afterJobId = null;
        BatchTotals totals = BatchTotals.ZERO;
        boolean hasMore = true;
        while (hasMore) {
            UUID cursor = afterJobId;
            BatchPageResult page =
                    Objects.requireNonNull(
                            transactionOperations.execute(_ -> processBatch(userId, cursor)));
            totals = totals.add(page.totals());
            afterJobId = page.lastJobId();
            hasMore = page.fetchedCount() == BATCH_SIZE;
        }
        return totals;
    }

    private BatchPageResult processBatch(UserId userId, @Nullable UUID afterJobId) {
        List<PendingCoordinateJob> batch =
                enrichmentJobRepository.findPendingCoordinateLookups(
                        userId, afterJobId, BATCH_SIZE);
        if (batch.isEmpty()) {
            return new BatchPageResult(BatchTotals.ZERO, null, 0);
        }
        BatchTotals batchTotals =
                batch.stream()
                        .map(this::materializeSingleJob)
                        .reduce(BatchTotals.ZERO, BatchTotals::add);
        return new BatchPageResult(batchTotals, batch.getLast().jobId(), batch.size());
    }

    private BatchTotals materializeSingleJob(PendingCoordinateJob job) {
        if (job.recordType() == ImportRecordKind.SAVED_CSV) {
            return materializeCsvJob(job);
        }
        if (job.recordType() == ImportRecordKind.REVIEW_GEOJSON) {
            return materializeReviewJob(job);
        }
        return BatchTotals.ZERO;
    }

    private BatchTotals materializeCsvJob(PendingCoordinateJob job) {
        JsonNode payload = parsePayload(job.payloadJson());
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
        if (cid != null) {
            Optional<PlaceId> matchedId =
                    atlasFacade.attachCsvMembershipByCid(
                            new AttachCsvMembershipCommand(
                                    cid,
                                    canonicalTitle,
                                    titleCoords.orElse(null),
                                    classification.category(),
                                    classification.subcategory(),
                                    classification.source(),
                                    job.accountLabel(),
                                    listName,
                                    listKind,
                                    note,
                                    tags,
                                    url,
                                    null,
                                    null,
                                    null,
                                    null));
            if (matchedId.isPresent()) {
                enrichmentJobRepository.markCoordinateJobCompleted(job.jobId(), matchedId.get());
                return new BatchTotals(0, 0, 1);
            }
        }

        if (titleCoords.isPresent()) {
            UpsertedPlaceView upserted =
                    upsertResolvedCsv(
                            job,
                            canonicalTitle,
                            titleCoords.get(),
                            CoordSource.TAKEOUT,
                            cid,
                            classification,
                            listName,
                            listKind,
                            note,
                            tags,
                            url);
            enrichmentJobRepository.markCoordinateJobCompleted(job.jobId(), upserted.placeId());
            return new BatchTotals(1, 0, 0);
        }

        Optional<GeoPoint> s2Coords = GoogleS2CellDecoder.decodeFromUrl(url);
        if (s2Coords.isPresent()) {
            UpsertedPlaceView upserted =
                    upsertResolvedCsv(
                            job,
                            canonicalTitle,
                            s2Coords.get(),
                            CoordSource.TAKEOUT_S2_APPROX,
                            cid,
                            classification,
                            listName,
                            listKind,
                            note,
                            tags,
                            url);
            enrichmentJobRepository.markCoordinateJobCompleted(job.jobId(), upserted.placeId());
            return new BatchTotals(0, 1, 0);
        }

        return BatchTotals.ZERO;
    }

    private BatchTotals materializeReviewJob(PendingCoordinateJob job) {
        JsonNode payload = parsePayload(job.payloadJson());
        String rawName = firstNonBlank(job.rawTitle(), readText(payload, "name"));
        String canonicalName = rawName == null ? FALLBACK_REVIEW_NAME : rawName;
        String url = firstNonBlank(job.rawUrl(), readText(payload, "googleMapsUrl"));
        String reviewText = readText(payload, "reviewText");
        Long cid = resolveCid(job.googleCid(), url);
        PlaceClassification classification =
                PlaceCategoryClassifier.classify(canonicalName, reviewText, null);

        Optional<GeoPoint> titleCoords = TitleCoordinateParser.parse(rawName);
        if (cid != null) {
            Optional<PlaceId> matchedId =
                    atlasFacade.attachCsvMembershipByCid(
                            new AttachCsvMembershipCommand(
                                    cid,
                                    canonicalName,
                                    titleCoords.orElse(null),
                                    classification.category(),
                                    classification.subcategory(),
                                    classification.source(),
                                    job.accountLabel(),
                                    LIST_NAME_REVIEWS,
                                    ListKind.REVIEW,
                                    reviewText,
                                    null,
                                    url,
                                    job.observedAt(),
                                    VisitStatus.VISITED,
                                    VisitSource.REVIEW,
                                    job.observedAt()));
            if (matchedId.isPresent()) {
                enrichmentJobRepository.markCoordinateJobCompleted(job.jobId(), matchedId.get());
                return new BatchTotals(0, 0, 1);
            }
        }

        if (titleCoords.isPresent()) {
            UpsertedPlaceView upserted =
                    upsertResolvedReview(
                            job,
                            canonicalName,
                            titleCoords.get(),
                            CoordSource.TAKEOUT,
                            cid,
                            classification,
                            reviewText,
                            url);
            enrichmentJobRepository.markCoordinateJobCompleted(job.jobId(), upserted.placeId());
            return new BatchTotals(1, 0, 0);
        }

        Optional<GeoPoint> s2Coords = GoogleS2CellDecoder.decodeFromUrl(url);
        if (s2Coords.isPresent()) {
            UpsertedPlaceView upserted =
                    upsertResolvedReview(
                            job,
                            canonicalName,
                            s2Coords.get(),
                            CoordSource.TAKEOUT_S2_APPROX,
                            cid,
                            classification,
                            reviewText,
                            url);
            enrichmentJobRepository.markCoordinateJobCompleted(job.jobId(), upserted.placeId());
            return new BatchTotals(0, 1, 0);
        }

        return BatchTotals.ZERO;
    }

    private UpsertedPlaceView upsertResolvedCsv(
            PendingCoordinateJob job,
            String canonicalName,
            GeoPoint coordinates,
            CoordSource coordSource,
            @Nullable Long cid,
            PlaceClassification classification,
            String listName,
            ListKind listKind,
            @Nullable String note,
            @Nullable String tags,
            @Nullable String url) {
        return atlasFacade.upsertImportedPlace(
                new ImportedPlaceUpsertCommand(
                        canonicalName,
                        coordinates,
                        coordSource,
                        null,
                        cid,
                        classification.category(),
                        classification.subcategory(),
                        classification.source(),
                        null,
                        null,
                        job.accountLabel(),
                        listName,
                        listKind,
                        note,
                        tags,
                        url,
                        null,
                        null,
                        null,
                        null));
    }

    private UpsertedPlaceView upsertResolvedReview(
            PendingCoordinateJob job,
            String canonicalName,
            GeoPoint coordinates,
            CoordSource coordSource,
            @Nullable Long cid,
            PlaceClassification classification,
            @Nullable String reviewText,
            @Nullable String url) {
        return atlasFacade.upsertImportedPlace(
                new ImportedPlaceUpsertCommand(
                        canonicalName,
                        coordinates,
                        coordSource,
                        null,
                        cid,
                        classification.category(),
                        classification.subcategory(),
                        classification.source(),
                        null,
                        null,
                        job.accountLabel(),
                        LIST_NAME_REVIEWS,
                        ListKind.REVIEW,
                        reviewText,
                        null,
                        url,
                        job.observedAt(),
                        VisitStatus.VISITED,
                        VisitSource.REVIEW,
                        job.observedAt()));
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

    private record BatchTotals(
            int exactFromTitleCount, int approxFromS2Count, int mergedByCidCount) {

        private static final BatchTotals ZERO = new BatchTotals(0, 0, 0);

        BatchTotals add(BatchTotals other) {
            return new BatchTotals(
                    exactFromTitleCount + other.exactFromTitleCount,
                    approxFromS2Count + other.approxFromS2Count,
                    mergedByCidCount + other.mergedByCidCount);
        }
    }

    private record BatchPageResult(
            BatchTotals totals, @Nullable UUID lastJobId, int fetchedCount) {}

    private record FinalQueueCounts(int enqueuedEnrichmentCount, int remainingLookupCount) {}
}
