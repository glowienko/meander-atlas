package com.meanderatlas.ingestion.domain.source;

import com.meanderatlas.shared.UserId;
import java.util.List;
import java.util.UUID;

public interface ImportRepository {

    boolean hasCompletedBatch(UserId userId, String accountLabel, String contentSha256);

    UUID startOrRestartBatch(
            UserId userId, String accountLabel, String contentSha256, String sourceType);

    void insertRecords(
            UUID batchId, UserId userId, String accountLabel, List<NewImportRecord> records);

    void enqueueCoordinateLookups(UserId userId, List<NewResolutionJob> jobs);

    void markBatchCompleted(UUID batchId, int recordCount);

    ImportStatusSummary fetchStatus(UserId userId);
}
