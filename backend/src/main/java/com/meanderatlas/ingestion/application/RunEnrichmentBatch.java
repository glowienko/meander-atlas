package com.meanderatlas.ingestion.application;

import com.meanderatlas.atlas.AtlasFacade;
import com.meanderatlas.atlas.ImportedPlaceUpsertCommand;
import com.meanderatlas.atlas.PlaceEnrichmentCommand;
import com.meanderatlas.atlas.PlaceEnrichmentResultView;
import com.meanderatlas.atlas.PlaceEnrichmentTargetView;
import com.meanderatlas.atlas.UpsertedPlaceView;
import com.meanderatlas.ingestion.EnrichmentBatchResultView;
import com.meanderatlas.ingestion.adapter.http.EnrichmentProperties;
import com.meanderatlas.ingestion.application.PendingJobMetadataExtractor.ExtractedJobMetadata;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.AnchoredEnrichmentTarget;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.EnrichmentLookupOutcome;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.OpenPoiMatch;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.UnanchoredLookupTarget;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiMatcher;
import com.meanderatlas.ingestion.domain.source.EnrichmentJobRepository;
import com.meanderatlas.ingestion.domain.source.EnrichmentJobRepository.ClaimedResolutionJob;
import com.meanderatlas.ingestion.domain.source.EnrichmentJobRepository.PendingCoordinateJob;
import com.meanderatlas.ingestion.domain.source.ResolutionJobType;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RunEnrichmentBatch {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 500;
    private static final long BASE_RETRY_SECONDS = 30L;
    private static final int MAX_BACKOFF_SHIFT = 5;
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final EnrichmentProperties properties;
    private final EnrichmentJobRepository enrichmentJobRepository;
    private final OpenPoiCatalogPort openPoiCatalogPort;
    private final AtlasFacade atlasFacade;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionOperations transactionOperations;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    public RunEnrichmentBatch(
            EnrichmentProperties properties,
            EnrichmentJobRepository enrichmentJobRepository,
            OpenPoiCatalogPort openPoiCatalogPort,
            AtlasFacade atlasFacade,
            CurrentUserProvider currentUserProvider,
            TransactionOperations transactionOperations) {
        this.properties = properties;
        this.enrichmentJobRepository = enrichmentJobRepository;
        this.openPoiCatalogPort = openPoiCatalogPort;
        this.atlasFacade = atlasFacade;
        this.currentUserProvider = currentUserProvider;
        this.transactionOperations = transactionOperations;
    }

    public boolean isRunning() {
        return running.get();
    }

    @PreDestroy
    void onShutdown() {
        stopRequested.set(true);
    }

    public EnrichmentBatchResultView execute(int limit, boolean dryRun, boolean background) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "limit must be between " + MIN_LIMIT + " and " + MAX_LIMIT);
        }
        UserId userId = currentUserProvider.currentUserId();
        if (!properties.enabled()) {
            int remaining = countRemainingInReadTx(userId);
            return new EnrichmentBatchResultView(
                    false, dryRun, false, false, 0, 0, 0, 0, 0, 0, 0, remaining);
        }
        if (stopRequested.get()) {
            int remaining = countRemainingInReadTx(userId);
            return new EnrichmentBatchResultView(
                    true, dryRun, false, false, 0, 0, 0, 0, 0, 0, 0, remaining);
        }
        if (!running.compareAndSet(false, true)) {
            int remaining = countRemainingInReadTx(userId);
            return new EnrichmentBatchResultView(
                    true, dryRun, false, true, 0, 0, 0, 0, 0, 0, 0, remaining);
        }
        if (background) {
            int remaining = countRemainingInReadTx(userId);
            Thread.ofVirtual()
                    .name("enrichment-drain")
                    .start(() -> {
                        try {
                            drainLoop(userId, limit, dryRun);
                        } finally {
                            running.set(false);
                        }
                    });
            return new EnrichmentBatchResultView(
                    true, dryRun, true, false, 0, 0, 0, 0, 0, 0, 0, remaining);
        }
        try {
            return runSingleBatch(userId, limit, dryRun);
        } finally {
            running.set(false);
        }
    }

    private void drainLoop(UserId userId, int limit, boolean dryRun) {
        int batchLimit = Math.min(limit, Math.max(1, properties.batchSize()));
        boolean keepGoing = true;
        while (keepGoing && !stopRequested.get()) {
            EnrichmentBatchResultView batch = runSingleBatch(userId, batchLimit, dryRun);
            keepGoing = !dryRun && batch.claimedCount() > 0 && batch.remainingPendingCount() > 0;
        }
    }

    private EnrichmentBatchResultView runSingleBatch(
            UserId userId, int batchLimit, boolean dryRun) {
        ClaimedBatch claimedBatch =
                Objects.requireNonNull(
                        transactionOperations.execute(_ -> {
                            List<ClaimedResolutionJob> claimed =
                                    enrichmentJobRepository.claimPendingEnrichmentJobs(
                                            userId, batchLimit);
                            if (claimed.isEmpty()) {
                                return new ClaimedBatch(List.of(), Map.of());
                            }
                            List<PlaceId> targetIds =
                                    claimed.stream()
                                            .filter(job ->
                                                    job.jobType()
                                                                    == ResolutionJobType
                                                                            .OPEN_CATALOG_ENRICH
                                                            && job.placeId() != null)
                                            .map(ClaimedResolutionJob::placeId)
                                            .filter(Objects::nonNull)
                                            .distinct()
                                            .toList();
                            Map<PlaceId, PlaceEnrichmentTargetView> targetsById =
                                    targetIds.isEmpty()
                                            ? Map.of()
                                            : atlasFacade.findEnrichmentTargets(targetIds).stream()
                                                    .collect(
                                                            Collectors.toUnmodifiableMap(
                                                                    PlaceEnrichmentTargetView
                                                                            ::placeId,
                                                                    Function.identity(),
                                                                    (a, _) -> a));
                            return new ClaimedBatch(claimed, targetsById);
                        }));

        if (claimedBatch.jobs().isEmpty()) {
            int remaining = countRemainingInReadTx(userId);
            return new EnrichmentBatchResultView(
                    true, dryRun, false, false, 0, 0, 0, 0, 0, 0, 0, remaining);
        }

        List<JobOutcomeEntry> outcomes = resolveOutsideTransaction(claimedBatch);

        return Objects.requireNonNull(
                transactionOperations.execute(
                        _ -> persistOrRelease(userId, claimedBatch, outcomes, dryRun)));
    }

    private List<JobOutcomeEntry> resolveOutsideTransaction(ClaimedBatch claimedBatch) {
        List<AnchoredEnrichmentTarget> anchoredTargets =
                claimedBatch.jobs().stream()
                        .filter(job ->
                                job.jobType() == ResolutionJobType.OPEN_CATALOG_ENRICH
                                        && job.placeId() != null)
                        .map(job -> claimedBatch.targetsById().get(job.placeId()))
                        .filter(Objects::nonNull)
                        .map(RunEnrichmentBatch::toAnchoredTarget)
                        .distinct()
                        .toList();

        Map<PlaceId, EnrichmentLookupOutcome> anchoredOutcomes =
                anchoredTargets.isEmpty()
                        ? Map.of()
                        : openPoiCatalogPort.enrichAnchoredBatch(anchoredTargets);

        return claimedBatch.jobs().stream()
                .map(job -> resolveSingleJob(job, claimedBatch.targetsById(), anchoredOutcomes))
                .toList();
    }

    private JobOutcomeEntry resolveSingleJob(
            ClaimedResolutionJob job,
            Map<PlaceId, PlaceEnrichmentTargetView> targetsById,
            Map<PlaceId, EnrichmentLookupOutcome> anchoredOutcomes) {
        if (job.jobType() == ResolutionJobType.OPEN_CATALOG_ENRICH) {
            PlaceId placeId = job.placeId();
            PlaceEnrichmentTargetView target = placeId == null ? null : targetsById.get(placeId);
            if (target == null) {
                return new JobOutcomeEntry(
                        job,
                        null,
                        null,
                        new EnrichmentLookupOutcome.NoMatch("Target place not found"));
            }
            EnrichmentLookupOutcome outcome =
                    anchoredOutcomes.getOrDefault(
                            target.placeId(),
                            new EnrichmentLookupOutcome.NoMatch("No match returned for place"));
            return new JobOutcomeEntry(job, target, null, outcome);
        }
        PendingCoordinateJob coordJob = job.coordinateJob();
        if (coordJob == null) {
            return new JobOutcomeEntry(
                    job,
                    null,
                    null,
                    new EnrichmentLookupOutcome.NoMatch("Missing coordinate lookup record"));
        }
        ExtractedJobMetadata metadata = PendingJobMetadataExtractor.extract(coordJob);
        if (OpenPoiMatcher.isGenericPlaceholder(metadata.canonicalTitle())) {
            return new JobOutcomeEntry(
                    job,
                    null,
                    metadata,
                    new EnrichmentLookupOutcome.NoMatch("Placeholder title cannot be geocoded"));
        }
        EnrichmentLookupOutcome outcome =
                openPoiCatalogPort.resolveUnanchored(
                        new UnanchoredLookupTarget(
                                job.jobId(), metadata.canonicalTitle(), metadata.url(), null));
        return new JobOutcomeEntry(job, null, metadata, outcome);
    }

    private EnrichmentBatchResultView persistOrRelease(
            UserId userId,
            ClaimedBatch claimedBatch,
            List<JobOutcomeEntry> outcomes,
            boolean dryRun) {
        if (dryRun) {
            List<UUID> jobIds =
                    claimedBatch.jobs().stream().map(ClaimedResolutionJob::jobId).toList();
            enrichmentJobRepository.releaseOnDryRun(jobIds);
            BatchStats stats =
                    outcomes.stream()
                            .map(RunEnrichmentBatch::computeDryRunStats)
                            .reduce(BatchStats.ZERO, BatchStats::add);
            int remaining = enrichmentJobRepository.countRemainingPendingJobs(userId);
            return new EnrichmentBatchResultView(
                    true,
                    true,
                    false,
                    false,
                    claimedBatch.jobs().size(),
                    stats.matched(),
                    stats.upgradedCoords(),
                    stats.upgradedCategory(),
                    stats.photosAdded(),
                    stats.failed(),
                    stats.skipped(),
                    remaining);
        }

        BatchStats stats =
                outcomes.stream().map(this::applyOutcome).reduce(BatchStats.ZERO, BatchStats::add);
        int remaining = enrichmentJobRepository.countRemainingPendingJobs(userId);
        return new EnrichmentBatchResultView(
                true,
                false,
                false,
                false,
                claimedBatch.jobs().size(),
                stats.matched(),
                stats.upgradedCoords(),
                stats.upgradedCategory(),
                stats.photosAdded(),
                stats.failed(),
                stats.skipped(),
                remaining);
    }

    private BatchStats applyOutcome(JobOutcomeEntry entry) {
        ClaimedResolutionJob job = entry.job();
        return switch (entry.outcome()) {
            case EnrichmentLookupOutcome.Matched(OpenPoiMatch match) ->
                    applyMatchedOutcome(job, entry.metadata(), match);
            case EnrichmentLookupOutcome.NoMatch(String reason) -> {
                enrichmentJobRepository.markJobSkipped(job.jobId(), reason);
                yield new BatchStats(0, 0, 0, 0, 0, 1);
            }
            case EnrichmentLookupOutcome.TransientFailure(String error) -> {
                if (job.attempts() >= Math.max(1, properties.maxRetries())) {
                    enrichmentJobRepository.markJobSkipped(job.jobId(), error);
                    yield new BatchStats(0, 0, 0, 0, 0, 1);
                }
                Duration backoff =
                        Duration.ofSeconds(
                                BASE_RETRY_SECONDS
                                        << Math.min(
                                                Math.max(0, job.attempts() - 1),
                                                MAX_BACKOFF_SHIFT));
                enrichmentJobRepository.markJobFailed(
                        job.jobId(), error, Instant.now().plus(backoff));
                yield new BatchStats(0, 0, 0, 0, 1, 0);
            }
        };
    }

    private BatchStats applyMatchedOutcome(
            ClaimedResolutionJob job, @Nullable ExtractedJobMetadata metadata, OpenPoiMatch match) {
        String openingHoursJson = serializeOpeningHours(match.openingHoursRaw());
        if (job.jobType() == ResolutionJobType.OPEN_CATALOG_ENRICH) {
            PlaceId placeId = Objects.requireNonNull(job.placeId());
            PlaceEnrichmentResultView result =
                    atlasFacade.applyPlaceEnrichment(
                            new PlaceEnrichmentCommand(
                                    placeId,
                                    match.candidate().coordinates(),
                                    match.candidate().osmId(),
                                    match.category(),
                                    match.subcategory(),
                                    match.cityName(),
                                    match.photoUrl(),
                                    openingHoursJson));
            enrichmentJobRepository.markJobCompleted(job.jobId(), placeId);
            return new BatchStats(
                    1,
                    result.coordsUpgraded() ? 1 : 0,
                    result.categoryUpgraded() ? 1 : 0,
                    result.photoAdded() ? 1 : 0,
                    0,
                    0);
        }

        PendingCoordinateJob coordJob = Objects.requireNonNull(job.coordinateJob());
        ExtractedJobMetadata resolvedMetadata = Objects.requireNonNull(metadata);
        boolean hasCatalogCategory = match.category() != PlaceCategory.UNCATEGORIZED;
        PlaceCategory initialCategory =
                hasCatalogCategory
                        ? match.category()
                        : resolvedMetadata.classification().category();
        String initialSubcategory =
                hasCatalogCategory
                        ? match.subcategory()
                        : resolvedMetadata.classification().subcategory();
        CategorySource initialCategorySource =
                hasCatalogCategory
                        ? CategorySource.OPEN_CATALOG
                        : resolvedMetadata.classification().source();

        UpsertedPlaceView upserted =
                atlasFacade.upsertImportedPlace(
                        new ImportedPlaceUpsertCommand(
                                resolvedMetadata.canonicalTitle(),
                                match.candidate().coordinates(),
                                CoordSource.OPEN_CATALOG,
                                null,
                                resolvedMetadata.cid(),
                                initialCategory,
                                initialSubcategory,
                                initialCategorySource,
                                match.candidate().countryCode(),
                                match.cityName(),
                                coordJob.accountLabel(),
                                resolvedMetadata.listName(),
                                resolvedMetadata.listKind(),
                                resolvedMetadata.note(),
                                resolvedMetadata.tags(),
                                resolvedMetadata.url(),
                                resolvedMetadata.savedAt(),
                                resolvedMetadata.visitStatus(),
                                resolvedMetadata.visitSource(),
                                resolvedMetadata.visitedAt()));
        PlaceEnrichmentResultView enriched =
                atlasFacade.applyPlaceEnrichment(
                        new PlaceEnrichmentCommand(
                                upserted.placeId(),
                                match.candidate().coordinates(),
                                match.candidate().osmId(),
                                hasCatalogCategory ? match.category() : null,
                                hasCatalogCategory ? match.subcategory() : null,
                                match.cityName(),
                                match.photoUrl(),
                                openingHoursJson));
        enrichmentJobRepository.markJobCompleted(job.jobId(), upserted.placeId());
        int coordsUpgraded = upserted.created() || enriched.coordsUpgraded() ? 1 : 0;
        int categoryUpgraded =
                (upserted.created() && hasCatalogCategory) || enriched.categoryUpgraded() ? 1 : 0;
        int photoAdded = enriched.photoAdded() ? 1 : 0;
        return new BatchStats(1, coordsUpgraded, categoryUpgraded, photoAdded, 0, 0);
    }

    private static BatchStats computeDryRunStats(JobOutcomeEntry entry) {
        return switch (entry.outcome()) {
            case EnrichmentLookupOutcome.Matched(OpenPoiMatch match) -> {
                PlaceEnrichmentTargetView target = entry.target();
                if (target == null) {
                    yield new BatchStats(
                            1,
                            1,
                            match.category() != PlaceCategory.UNCATEGORIZED ? 1 : 0,
                            match.photoUrl() != null ? 1 : 0,
                            0,
                            0);
                }
                int coords = target.coordSource() == CoordSource.TAKEOUT_S2_APPROX ? 1 : 0;
                int cat =
                        match.category() != PlaceCategory.UNCATEGORIZED
                                        && target.categorySource() != CategorySource.MANUAL
                                        && (target.categorySource() != CategorySource.OPEN_CATALOG
                                                || target.category() != match.category())
                                ? 1
                                : 0;
                int photo =
                        match.photoUrl() != null
                                        && (target.photoUrl() == null
                                                || target.photoUrl().isBlank())
                                ? 1
                                : 0;
                yield new BatchStats(1, coords, cat, photo, 0, 0);
            }
            case EnrichmentLookupOutcome.NoMatch _ -> new BatchStats(0, 0, 0, 0, 0, 1);
            case EnrichmentLookupOutcome.TransientFailure _ -> new BatchStats(0, 0, 0, 0, 1, 0);
        };
    }

    private int countRemainingInReadTx(UserId userId) {
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
            @Nullable PlaceEnrichmentTargetView target,
            @Nullable ExtractedJobMetadata metadata,
            EnrichmentLookupOutcome outcome) {}

    private record BatchStats(
            int matched,
            int upgradedCoords,
            int upgradedCategory,
            int photosAdded,
            int failed,
            int skipped) {

        private static final BatchStats ZERO = new BatchStats(0, 0, 0, 0, 0, 0);

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
