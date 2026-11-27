package com.meanderatlas.ingestion.application;

import com.meanderatlas.atlas.AtlasFacade;
import com.meanderatlas.atlas.PlaceEnrichmentCommand;
import com.meanderatlas.atlas.PlaceEnrichmentResultView;
import com.meanderatlas.atlas.PlaceEnrichmentTargetView;
import com.meanderatlas.atlas.UpsertedPlaceView;
import com.meanderatlas.ingestion.EnrichmentBatchResultView;
import com.meanderatlas.ingestion.domain.classification.PlaceClassification;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.AnchoredEnrichmentTarget;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.EnrichmentLookupOutcome;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.OpenPoiMatch;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.UnanchoredLookupTarget;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiMatcher;
import com.meanderatlas.ingestion.domain.source.EnrichmentJobRepository;
import com.meanderatlas.ingestion.domain.source.EnrichmentJobRepository.ClaimedResolutionJob;
import com.meanderatlas.ingestion.domain.source.EnrichmentJobRepository.PendingCoordinateJob;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RunEnrichmentBatch {

    private static final Logger LOG = LoggerFactory.getLogger(RunEnrichmentBatch.class);
    private static final Duration BASE_RETRY_DELAY = Duration.ofSeconds(30);
    private static final int MAX_BACKOFF_SHIFT = 5;
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final EnrichmentProperties properties;
    private final EnrichmentJobRepository enrichmentJobRepository;
    private final OpenPoiCatalogPort openPoiCatalogPort;
    private final AtlasFacade atlasFacade;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionOperations transactionOperations;
    private final TaskExecutor taskExecutor;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    public RunEnrichmentBatch(
            EnrichmentProperties properties,
            EnrichmentJobRepository enrichmentJobRepository,
            OpenPoiCatalogPort openPoiCatalogPort,
            AtlasFacade atlasFacade,
            CurrentUserProvider currentUserProvider,
            TransactionOperations transactionOperations,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
            Clock clock) {
        this.properties = properties;
        this.enrichmentJobRepository = enrichmentJobRepository;
        this.openPoiCatalogPort = openPoiCatalogPort;
        this.atlasFacade = atlasFacade;
        this.currentUserProvider = currentUserProvider;
        this.transactionOperations = transactionOperations;
        this.taskExecutor = taskExecutor;
        this.clock = clock;
    }

    public boolean isRunning() {
        return running.get();
    }

    @PreDestroy
    void onShutdown() {
        stopRequested.set(true);
    }

    public EnrichmentBatchResultView runBatch(int limit, boolean dryRun) {
        UserId userId = currentUserProvider.currentUserId();
        return rejectUnlessAcquired(userId, dryRun)
                .orElseGet(() -> {
                    try {
                        return runSingleBatch(userId, limit, dryRun);
                    } finally {
                        running.set(false);
                    }
                });
    }

    public EnrichmentBatchResultView startDrain(int limit, boolean dryRun) {
        UserId userId = currentUserProvider.currentUserId();
        Optional<EnrichmentBatchResultView> rejected = rejectUnlessAcquired(userId, dryRun);
        if (rejected.isPresent()) {
            return rejected.get();
        }
        int remaining = countRemaining(userId);
        LOG.info(
                "Starting background enrichment drain (limit={}, dryRun={}, remaining={})",
                limit,
                dryRun,
                remaining);
        try {
            taskExecutor.execute(() -> drain(userId, limit, dryRun));
        } catch (TaskRejectedException ex) {
            running.set(false);
            throw ex;
        }
        return EnrichmentBatchResultView.backgroundStarted(dryRun, remaining);
    }

    private Optional<EnrichmentBatchResultView> rejectUnlessAcquired(
            UserId userId, boolean dryRun) {
        if (!properties.enabled()) {
            return Optional.of(EnrichmentBatchResultView.disabled(dryRun, countRemaining(userId)));
        }
        if (stopRequested.get()) {
            return Optional.of(EnrichmentBatchResultView.idle(dryRun, countRemaining(userId)));
        }
        if (!running.compareAndSet(false, true)) {
            return Optional.of(EnrichmentBatchResultView.busy(dryRun, countRemaining(userId)));
        }
        return Optional.empty();
    }

    private void drain(UserId userId, int limit, boolean dryRun) {
        try {
            int batchLimit = Math.min(limit, Math.max(1, properties.batchSize()));
            boolean keepGoing = true;
            while (keepGoing && !stopRequested.get()) {
                EnrichmentBatchResultView batch = runSingleBatch(userId, batchLimit, dryRun);
                keepGoing =
                        !dryRun && batch.claimedCount() > 0 && batch.remainingPendingCount() > 0;
            }
        } catch (RuntimeException ex) {
            LOG.error("Background enrichment drain aborted unexpectedly", ex);
        } finally {
            running.set(false);
        }
    }

    private EnrichmentBatchResultView runSingleBatch(UserId userId, int limit, boolean dryRun) {
        ClaimedBatch batch =
                Objects.requireNonNull(transactionOperations.execute(_ -> claim(userId, limit)));
        if (batch.jobs().isEmpty()) {
            return EnrichmentBatchResultView.idle(dryRun, countRemaining(userId));
        }
        LOG.info(
                "Claimed {} enrichment jobs (dryRun={}, anchoredTargets={})",
                batch.jobs().size(),
                dryRun,
                batch.targetsById().size());
        BatchStats stats;
        try {
            List<JobOutcomeEntry> outcomes = resolveOutsideTransaction(batch);
            stats =
                    Objects.requireNonNull(
                            transactionOperations.execute(status -> {
                                if (dryRun) {
                                    status.setRollbackOnly();
                                }
                                return outcomes.stream()
                                        .map(this::applyOutcome)
                                        .reduce(BatchStats.ZERO, BatchStats::add);
                            }));
        } catch (RuntimeException ex) {
            LOG.error("Enrichment batch failed; failing {} claimed jobs", batch.jobs().size(), ex);
            if (!dryRun) {
                String error = Objects.requireNonNullElse(ex.getMessage(), ex.toString());
                transactionOperations.executeWithoutResult(
                        _ -> batch.jobs().forEach(job -> failOrSkip(job, error)));
            }
            throw ex;
        } finally {
            if (dryRun) {
                List<UUID> jobIds = batch.jobs().stream().map(ClaimedResolutionJob::jobId).toList();
                transactionOperations.executeWithoutResult(
                        _ -> enrichmentJobRepository.releaseDryRunClaims(jobIds));
            }
        }
        EnrichmentBatchResultView result =
                new EnrichmentBatchResultView(
                        true,
                        dryRun,
                        false,
                        false,
                        batch.jobs().size(),
                        stats.matched(),
                        stats.upgradedCoords(),
                        stats.upgradedCategory(),
                        stats.photosAdded(),
                        stats.failed(),
                        stats.skipped(),
                        countRemaining(userId));
        LOG.info("Enrichment batch finished: {}", result);
        return result;
    }

    private ClaimedBatch claim(UserId userId, int limit) {
        List<ClaimedResolutionJob> claimed =
                enrichmentJobRepository.claimPendingEnrichmentJobs(userId, limit);
        List<PlaceId> targetIds =
                claimed.stream()
                        .map(ClaimedResolutionJob::enrichmentPlaceId)
                        .flatMap(Optional::stream)
                        .distinct()
                        .toList();
        Map<PlaceId, PlaceEnrichmentTargetView> targetsById =
                targetIds.isEmpty()
                        ? Map.of()
                        : atlasFacade.findEnrichmentTargets(targetIds).stream()
                                .collect(
                                        Collectors.toUnmodifiableMap(
                                                PlaceEnrichmentTargetView::placeId,
                                                Function.identity(),
                                                (a, _) -> a));
        return new ClaimedBatch(claimed, targetsById);
    }

    private List<JobOutcomeEntry> resolveOutsideTransaction(ClaimedBatch batch) {
        List<AnchoredEnrichmentTarget> anchoredTargets =
                batch.targetsById().values().stream()
                        .map(RunEnrichmentBatch::toAnchoredTarget)
                        .toList();
        Map<PlaceId, EnrichmentLookupOutcome> anchoredOutcomes =
                anchoredTargets.isEmpty()
                        ? Map.of()
                        : openPoiCatalogPort.enrichAnchoredBatch(anchoredTargets);
        return batch.jobs().stream()
                .map(job ->
                        job.enrichmentPlaceId()
                                .map(placeId ->
                                        JobOutcomeEntry.anchored(
                                                job,
                                                resolveAnchored(
                                                        placeId,
                                                        batch.targetsById(),
                                                        anchoredOutcomes)))
                                .orElseGet(() -> resolveCoordinateJob(job)))
                .toList();
    }

    private static EnrichmentLookupOutcome resolveAnchored(
            PlaceId placeId,
            Map<PlaceId, PlaceEnrichmentTargetView> targetsById,
            Map<PlaceId, EnrichmentLookupOutcome> anchoredOutcomes) {
        if (!targetsById.containsKey(placeId)) {
            return new EnrichmentLookupOutcome.NoMatch("Target place not found");
        }
        return anchoredOutcomes.getOrDefault(
                placeId, new EnrichmentLookupOutcome.NoMatch("No match returned for place"));
    }

    private JobOutcomeEntry resolveCoordinateJob(ClaimedResolutionJob job) {
        PendingCoordinateJob coordinateJob = job.coordinateJob();
        if (coordinateJob == null) {
            return JobOutcomeEntry.noMatch(job, null, "Missing coordinate lookup record");
        }
        ImportedPlaceDraft draft = PendingJobMetadataExtractor.extract(coordinateJob);
        if (OpenPoiMatcher.isGenericPlaceholder(draft.canonicalTitle())) {
            return JobOutcomeEntry.noMatch(job, draft, "Placeholder title cannot be geocoded");
        }
        EnrichmentLookupOutcome outcome =
                openPoiCatalogPort.resolveUnanchored(
                        new UnanchoredLookupTarget(
                                job.jobId(), draft.canonicalTitle(), draft.url(), null));
        return new JobOutcomeEntry(job, draft, outcome);
    }

    private BatchStats applyOutcome(JobOutcomeEntry entry) {
        ClaimedResolutionJob job = entry.job();
        return switch (entry.outcome()) {
            case EnrichmentLookupOutcome.Matched(OpenPoiMatch match) ->
                    applyMatchedOutcome(job, entry.draft(), match);
            case EnrichmentLookupOutcome.NoMatch(String reason) -> {
                enrichmentJobRepository.markJobSkipped(job.jobId(), reason);
                yield BatchStats.SKIPPED;
            }
            case EnrichmentLookupOutcome.TransientFailure(String error) -> failOrSkip(job, error);
        };
    }

    private BatchStats failOrSkip(ClaimedResolutionJob job, String error) {
        if (job.attempts() >= Math.max(1, properties.maxRetries())) {
            enrichmentJobRepository.markJobSkipped(job.jobId(), error);
            return BatchStats.SKIPPED;
        }
        Duration backoff =
                BASE_RETRY_DELAY.multipliedBy(
                        1L << Math.min(Math.max(0, job.attempts() - 1), MAX_BACKOFF_SHIFT));
        enrichmentJobRepository.markJobFailed(job.jobId(), error, clock.instant().plus(backoff));
        return BatchStats.FAILED;
    }

    private BatchStats applyMatchedOutcome(
            ClaimedResolutionJob job, @Nullable ImportedPlaceDraft draft, OpenPoiMatch match) {
        boolean hasCatalogCategory = match.category() != PlaceCategory.UNCATEGORIZED;
        Optional<UpsertedPlaceView> upserted =
                Optional.ofNullable(draft)
                        .map(resolved ->
                                hasCatalogCategory
                                        ? resolved.withClassification(
                                                new PlaceClassification(
                                                        match.category(),
                                                        match.subcategory(),
                                                        CategorySource.OPEN_CATALOG))
                                        : resolved)
                        .map(resolved ->
                                atlasFacade.upsertImportedPlace(
                                        resolved.toUpsertCommand(
                                                match.candidate().coordinates(),
                                                CoordSource.OPEN_CATALOG,
                                                match.candidate().countryCode(),
                                                match.cityName())));
        PlaceId placeId =
                upserted.map(UpsertedPlaceView::placeId)
                        .orElseGet(() -> Objects.requireNonNull(job.placeId()));
        PlaceEnrichmentResultView enriched =
                atlasFacade.applyPlaceEnrichment(
                        new PlaceEnrichmentCommand(
                                placeId,
                                match.candidate().coordinates(),
                                match.candidate().osmId(),
                                match.category(),
                                match.subcategory(),
                                match.cityName(),
                                match.photoUrl(),
                                serializeOpeningHours(match.openingHoursRaw()),
                                match.candidate().name()));
        enrichmentJobRepository.markJobCompleted(job.jobId(), placeId);
        boolean created = upserted.map(UpsertedPlaceView::created).orElse(false);
        return BatchStats.matched(
                created || enriched.coordsUpgraded(),
                (created && hasCatalogCategory) || enriched.categoryUpgraded(),
                enriched.photoAdded());
    }

    private int countRemaining(UserId userId) {
        Integer count =
                transactionOperations.execute(
                        _ -> enrichmentJobRepository.countRemainingPendingJobs(userId));
        return count == null ? 0 : count;
    }

    private static AnchoredEnrichmentTarget toAnchoredTarget(PlaceEnrichmentTargetView view) {
        return new AnchoredEnrichmentTarget(
                view.placeId(),
                view.canonicalName(),
                new GeoPoint(view.lat(), view.lng()),
                view.coordSource(),
                view.categorySource(),
                view.osmId(),
                view.photoUrl(),
                view.countryCode(),
                view.cityName());
    }

    private static @Nullable String serializeOpeningHours(@Nullable String openingHoursRaw) {
        if (openingHoursRaw == null || openingHoursRaw.isBlank()) {
            return null;
        }
        return JSON_MAPPER.writeValueAsString(Map.of("raw", openingHoursRaw.trim()));
    }

    private record ClaimedBatch(
            List<ClaimedResolutionJob> jobs, Map<PlaceId, PlaceEnrichmentTargetView> targetsById) {}

    private record JobOutcomeEntry(
            ClaimedResolutionJob job,
            @Nullable ImportedPlaceDraft draft,
            EnrichmentLookupOutcome outcome) {

        static JobOutcomeEntry anchored(ClaimedResolutionJob job, EnrichmentLookupOutcome outcome) {
            return new JobOutcomeEntry(job, null, outcome);
        }

        static JobOutcomeEntry noMatch(
                ClaimedResolutionJob job, @Nullable ImportedPlaceDraft draft, String reason) {
            return new JobOutcomeEntry(job, draft, new EnrichmentLookupOutcome.NoMatch(reason));
        }
    }

    private record BatchStats(
            int matched,
            int upgradedCoords,
            int upgradedCategory,
            int photosAdded,
            int failed,
            int skipped) {

        private static final BatchStats ZERO = new BatchStats(0, 0, 0, 0, 0, 0);
        private static final BatchStats FAILED = new BatchStats(0, 0, 0, 0, 1, 0);
        private static final BatchStats SKIPPED = new BatchStats(0, 0, 0, 0, 0, 1);

        static BatchStats matched(
                boolean upgradedCoords, boolean upgradedCategory, boolean photoAdded) {
            return new BatchStats(
                    1, upgradedCoords ? 1 : 0, upgradedCategory ? 1 : 0, photoAdded ? 1 : 0, 0, 0);
        }

        BatchStats add(BatchStats other) {
            return new BatchStats(
                    matched + other.matched,
                    upgradedCoords + other.upgradedCoords,
                    upgradedCategory + other.upgradedCategory,
                    photosAdded + other.photosAdded,
                    failed + other.failed,
                    skipped + other.skipped);
        }
    }
}
