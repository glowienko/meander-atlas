package com.meanderatlas.ingestion.domain.source;

import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface EnrichmentJobRepository {

    List<PendingCoordinateJob> findPendingCoordinateLookups(
            UserId userId, @Nullable UUID afterJobId, int limit);

    void markCoordinateJobCompleted(UUID jobId, PlaceId placeId);

    int countPendingCoordinateLookups(UserId userId);

    int enqueueOpenCatalogEnrichments(UserId userId, List<PlaceId> placeIds);

    List<ClaimedResolutionJob> claimPendingEnrichmentJobs(UserId userId, int limit);

    void markJobCompleted(UUID jobId, @Nullable PlaceId placeId);

    void markJobFailed(UUID jobId, String error, Instant nextRunAt);

    void markJobSkipped(UUID jobId, String reason);

    void releaseDryRunClaims(List<UUID> jobIds);

    int countRemainingPendingJobs(UserId userId);

    record PendingCoordinateJob(
            UUID jobId,
            UUID importRecordId,
            String accountLabel,
            ImportRecordKind recordType,
            @Nullable String rawTitle,
            @Nullable String rawUrl,
            @Nullable Long googleCid,
            @Nullable Instant observedAt,
            String payloadJson) {}

    record ClaimedResolutionJob(
            UUID jobId,
            ResolutionJobType jobType,
            @Nullable PlaceId placeId,
            int attempts,
            @Nullable PendingCoordinateJob coordinateJob) {

        public Optional<PlaceId> enrichmentPlaceId() {
            return jobType == ResolutionJobType.OPEN_CATALOG_ENRICH
                    ? Optional.ofNullable(placeId)
                    : Optional.empty();
        }
    }
}
