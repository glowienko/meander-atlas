package com.meanderatlas.ingestion.application;

import com.meanderatlas.atlas.AtlasFacade;
import com.meanderatlas.ingestion.OfflineMaterializationSummaryView;
import com.meanderatlas.ingestion.application.ImportedPlaceResolver.Resolution;
import com.meanderatlas.ingestion.domain.source.EnrichmentJobRepository;
import com.meanderatlas.ingestion.domain.source.EnrichmentJobRepository.PendingCoordinateJob;
import com.meanderatlas.ingestion.domain.source.ImportRecordKind;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class MaterializePendingCoordinateJobs {

    private static final Logger LOG =
            LoggerFactory.getLogger(MaterializePendingCoordinateJobs.class);
    private static final int BATCH_SIZE = 200;

    private final EnrichmentJobRepository enrichmentJobRepository;
    private final AtlasFacade atlasFacade;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionOperations transactionOperations;
    private final ImportedPlaceResolver resolver;

    public MaterializePendingCoordinateJobs(
            EnrichmentJobRepository enrichmentJobRepository,
            AtlasFacade atlasFacade,
            CurrentUserProvider currentUserProvider,
            TransactionOperations transactionOperations) {
        this.enrichmentJobRepository = enrichmentJobRepository;
        this.atlasFacade = atlasFacade;
        this.currentUserProvider = currentUserProvider;
        this.transactionOperations = transactionOperations;
        this.resolver = new ImportedPlaceResolver(atlasFacade);
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
        OfflineMaterializationSummaryView summary =
                new OfflineMaterializationSummaryView(
                        materializedCount,
                        totals.exactFromTitleCount(),
                        totals.approxFromS2Count(),
                        totals.mergedByCidCount(),
                        finalCounts.remainingLookupCount(),
                        finalCounts.enqueuedEnrichmentCount());
        LOG.info(
                "Offline materialization finished (materialized={}, exactTitle={}, approxS2={}, mergedByCid={}, remainingLookup={}, enqueuedEnrichment={})",
                summary.materializedCount(),
                summary.exactFromTitleCount(),
                summary.approxFromS2Count(),
                summary.mergedByCidCount(),
                summary.remainingLookupCount(),
                summary.enqueuedEnrichmentCount());
        return summary;
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
        if (job.recordType() != ImportRecordKind.SAVED_CSV
                && job.recordType() != ImportRecordKind.REVIEW_GEOJSON) {
            return BatchTotals.ZERO;
        }
        return resolver.resolve(PendingJobMetadataExtractor.extract(job))
                .map(resolution -> {
                    enrichmentJobRepository.markCoordinateJobCompleted(
                            job.jobId(), resolution.placeId());
                    return BatchTotals.of(resolution);
                })
                .orElse(BatchTotals.ZERO);
    }

    private record BatchTotals(
            int exactFromTitleCount, int approxFromS2Count, int mergedByCidCount) {

        private static final BatchTotals ZERO = new BatchTotals(0, 0, 0);

        static BatchTotals of(Resolution resolution) {
            return switch (resolution) {
                case Resolution.CidMatched _ -> new BatchTotals(0, 0, 1);
                case Resolution.Materialized materialized ->
                        materialized.coordSource() == CoordSource.TAKEOUT
                                ? new BatchTotals(1, 0, 0)
                                : new BatchTotals(0, 1, 0);
            };
        }

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
