package com.meanderatlas.ingestion.application;

import com.meanderatlas.ingestion.domain.source.EnrichmentJobRepository;
import com.meanderatlas.ingestion.domain.source.ResolutionJobType;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

final class FakeEnrichmentJobRepository implements EnrichmentJobRepository {

    private final Map<UUID, JobState> jobs = new HashMap<>();
    private final Set<PlaceId> enqueuedEnrichmentPlaceIds = new HashSet<>();

    void addPendingLookup(PendingCoordinateJob job) {
        jobs.put(
                job.jobId(),
                new JobState(
                        job.jobId(),
                        ResolutionJobType.COORDINATE_LOOKUP,
                        job,
                        null,
                        "PENDING",
                        0,
                        null,
                        Instant.EPOCH));
    }

    UUID addPendingEnrichment(PlaceId placeId) {
        UUID jobId = UUID.randomUUID();
        enqueuedEnrichmentPlaceIds.add(placeId);
        jobs.put(
                jobId,
                new JobState(
                        jobId,
                        ResolutionJobType.OPEN_CATALOG_ENRICH,
                        null,
                        placeId,
                        "PENDING",
                        0,
                        null,
                        Instant.EPOCH));
        return jobId;
    }

    Set<PlaceId> enqueuedEnrichmentPlaceIds() {
        return Set.copyOf(enqueuedEnrichmentPlaceIds);
    }

    boolean isCompleted(UUID jobId) {
        JobState state = jobs.get(jobId);
        return state != null && "COMPLETED".equals(state.status);
    }

    String statusOf(UUID jobId) {
        JobState state = jobs.get(jobId);
        return state == null ? "MISSING" : state.status;
    }

    @Nullable String lastErrorOf(UUID jobId) {
        JobState state = jobs.get(jobId);
        return state == null ? null : state.lastError;
    }

    int attemptsOf(UUID jobId) {
        JobState state = jobs.get(jobId);
        return state == null ? 0 : state.attempts;
    }

    @Nullable PlaceId completedPlaceId(UUID jobId) {
        JobState state = jobs.get(jobId);
        return state == null ? null : state.placeId;
    }

    @Override
    public List<PendingCoordinateJob> findPendingCoordinateLookups(
            UserId userId, @Nullable UUID afterJobId, int limit) {
        String cursor = afterJobId == null ? null : afterJobId.toString();
        return jobs.values().stream()
                .filter(state -> state.jobType == ResolutionJobType.COORDINATE_LOOKUP)
                .filter(state -> "PENDING".equals(state.status))
                .map(state -> state.coordinateJob)
                .filter(java.util.Objects::nonNull)
                .filter(job -> cursor == null || job.jobId().toString().compareTo(cursor) > 0)
                .sorted(Comparator.comparing(job -> job.jobId().toString()))
                .limit(limit)
                .toList();
    }

    @Override
    public void markCoordinateJobCompleted(UUID jobId, PlaceId placeId) {
        JobState state = jobs.get(jobId);
        if (state != null) {
            state.status = "COMPLETED";
            state.placeId = placeId;
        }
    }

    @Override
    public int countPendingCoordinateLookups(UserId userId) {
        return (int)
                jobs.values().stream()
                        .filter(state -> state.jobType == ResolutionJobType.COORDINATE_LOOKUP)
                        .filter(state -> "PENDING".equals(state.status))
                        .count();
    }

    @Override
    public int enqueueOpenCatalogEnrichments(UserId userId, List<PlaceId> placeIds) {
        int added = 0;
        for (PlaceId placeId : placeIds.stream().distinct().toList()) {
            if (enqueuedEnrichmentPlaceIds.add(placeId)) {
                UUID jobId = UUID.randomUUID();
                jobs.put(
                        jobId,
                        new JobState(
                                jobId,
                                ResolutionJobType.OPEN_CATALOG_ENRICH,
                                null,
                                placeId,
                                "PENDING",
                                0,
                                null,
                                Instant.EPOCH));
                added++;
            }
        }
        return added;
    }

    @Override
    public List<ClaimedResolutionJob> claimPendingEnrichmentJobs(UserId userId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Instant now = Instant.now();
        List<JobState> claimed =
                jobs.values().stream()
                        .filter(state ->
                                "PENDING".equals(state.status) || "FAILED".equals(state.status))
                        .filter(state -> !state.nextRunAt.isAfter(now))
                        .sorted(
                                Comparator.comparing((JobState s) ->
                                                s.jobType != ResolutionJobType.OPEN_CATALOG_ENRICH)
                                        .thenComparing(s -> s.nextRunAt)
                                        .thenComparing(s -> s.jobId.toString()))
                        .limit(limit)
                        .toList();
        claimed.forEach(state -> {
            state.status = "IN_PROGRESS";
            state.attempts++;
        });
        return claimed.stream()
                .map(state ->
                        new ClaimedResolutionJob(
                                state.jobId,
                                state.jobType,
                                state.placeId,
                                state.attempts,
                                state.coordinateJob))
                .toList();
    }

    @Override
    public void markJobCompleted(UUID jobId, @Nullable PlaceId placeId) {
        JobState state = jobs.get(jobId);
        if (state != null) {
            state.status = "COMPLETED";
            if (placeId != null) {
                state.placeId = placeId;
            }
            state.lastError = null;
        }
    }

    @Override
    public void markJobFailed(UUID jobId, String error, Instant nextRunAt) {
        JobState state = jobs.get(jobId);
        if (state != null) {
            state.status = "FAILED";
            state.lastError = error;
            state.nextRunAt = nextRunAt;
        }
    }

    @Override
    public void markJobSkipped(UUID jobId, String reason) {
        JobState state = jobs.get(jobId);
        if (state != null) {
            state.status = "SKIPPED";
            state.lastError = reason;
        }
    }

    @Override
    public void releaseOnDryRun(List<UUID> jobIds) {
        for (UUID jobId : jobIds) {
            JobState state = jobs.get(jobId);
            if (state != null && "IN_PROGRESS".equals(state.status)) {
                state.status = state.attempts <= 1 ? "PENDING" : "FAILED";
                state.attempts = Math.max(0, state.attempts - 1);
            }
        }
    }

    @Override
    public int countRemainingPendingJobs(UserId userId) {
        return (int)
                jobs.values().stream()
                        .filter(state ->
                                "PENDING".equals(state.status) || "FAILED".equals(state.status))
                        .count();
    }

    private static final class JobState {
        private final UUID jobId;
        private final ResolutionJobType jobType;
        private final @Nullable PendingCoordinateJob coordinateJob;
        private @Nullable PlaceId placeId;
        private String status;
        private int attempts;
        private @Nullable String lastError;
        private Instant nextRunAt;

        private JobState(
                UUID jobId,
                ResolutionJobType jobType,
                @Nullable PendingCoordinateJob coordinateJob,
                @Nullable PlaceId placeId,
                String status,
                int attempts,
                @Nullable String lastError,
                Instant nextRunAt) {
            this.jobId = jobId;
            this.jobType = jobType;
            this.coordinateJob = coordinateJob;
            this.placeId = placeId;
            this.status = status;
            this.attempts = attempts;
            this.lastError = lastError;
            this.nextRunAt = nextRunAt;
        }
    }
}
