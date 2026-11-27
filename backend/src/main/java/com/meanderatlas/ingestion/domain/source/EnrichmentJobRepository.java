package com.meanderatlas.ingestion.domain.source;

import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface EnrichmentJobRepository {

    List<PendingCoordinateJob> findPendingCoordinateLookups(
            UserId userId, @Nullable UUID afterJobId, int limit);

    void markCoordinateJobCompleted(UUID jobId, PlaceId placeId);

    int countPendingCoordinateLookups(UserId userId);

    int enqueueOpenCatalogEnrichments(UserId userId, List<PlaceId> placeIds);

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
}
