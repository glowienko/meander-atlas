package com.meanderatlas.ingestion.application;

import com.meanderatlas.ingestion.domain.source.EnrichmentJobRepository;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

final class FakeEnrichmentJobRepository implements EnrichmentJobRepository {

    private final Map<UUID, JobState> lookupJobs = new HashMap<>();
    private final Set<PlaceId> enqueuedEnrichmentPlaceIds = new HashSet<>();

    void addPendingLookup(PendingCoordinateJob job) {
        lookupJobs.put(job.jobId(), new JobState(job, false, null));
    }

    Set<PlaceId> enqueuedEnrichmentPlaceIds() {
        return Set.copyOf(enqueuedEnrichmentPlaceIds);
    }

    boolean isCompleted(UUID jobId) {
        JobState state = lookupJobs.get(jobId);
        return state != null && state.completed;
    }

    @Nullable PlaceId completedPlaceId(UUID jobId) {
        JobState state = lookupJobs.get(jobId);
        return state == null ? null : state.placeId;
    }

    @Override
    public List<PendingCoordinateJob> findPendingCoordinateLookups(
            UserId userId, @Nullable UUID afterJobId, int limit) {
        String cursor = afterJobId == null ? null : afterJobId.toString();
        return lookupJobs.values().stream()
                .filter(state -> !state.completed)
                .map(state -> state.job)
                .filter(job -> cursor == null || job.jobId().toString().compareTo(cursor) > 0)
                .sorted(Comparator.comparing(job -> job.jobId().toString()))
                .limit(limit)
                .toList();
    }

    @Override
    public void markCoordinateJobCompleted(UUID jobId, PlaceId placeId) {
        JobState state = lookupJobs.get(jobId);
        if (state != null) {
            state.completed = true;
            state.placeId = placeId;
        }
    }

    @Override
    public int countPendingCoordinateLookups(UserId userId) {
        return (int) lookupJobs.values().stream().filter(state -> !state.completed).count();
    }

    @Override
    public int enqueueOpenCatalogEnrichments(UserId userId, List<PlaceId> placeIds) {
        int added = 0;
        for (PlaceId placeId : placeIds.stream().distinct().toList()) {
            if (enqueuedEnrichmentPlaceIds.add(placeId)) {
                added++;
            }
        }
        return added;
    }

    private static final class JobState {
        private final PendingCoordinateJob job;
        private boolean completed;
        private @Nullable PlaceId placeId;

        private JobState(PendingCoordinateJob job, boolean completed, @Nullable PlaceId placeId) {
            this.job = job;
            this.completed = completed;
            this.placeId = placeId;
        }
    }
}
