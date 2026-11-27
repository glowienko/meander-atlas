package com.meanderatlas.ingestion;

import com.meanderatlas.ingestion.application.GetImportStatus;
import com.meanderatlas.ingestion.application.ImportLocalDirectory;
import com.meanderatlas.ingestion.application.MaterializePendingCoordinateJobs;
import com.meanderatlas.ingestion.application.RunEnrichmentBatch;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class ImportFacade {

    private final ImportLocalDirectory importLocalDirectory;
    private final GetImportStatus getImportStatus;
    private final MaterializePendingCoordinateJobs materializePendingCoordinateJobs;
    private final RunEnrichmentBatch runEnrichmentBatch;

    public ImportFacade(
            ImportLocalDirectory importLocalDirectory,
            GetImportStatus getImportStatus,
            MaterializePendingCoordinateJobs materializePendingCoordinateJobs,
            RunEnrichmentBatch runEnrichmentBatch) {
        this.importLocalDirectory = importLocalDirectory;
        this.getImportStatus = getImportStatus;
        this.materializePendingCoordinateJobs = materializePendingCoordinateJobs;
        this.runEnrichmentBatch = runEnrichmentBatch;
    }

    public LocalImportSummaryView importLocalDirectory(@Nullable String account, boolean force) {
        return importLocalDirectory.execute(account, force);
    }

    public ImportStatusView status() {
        return getImportStatus.execute();
    }

    public OfflineMaterializationSummaryView materializeOffline() {
        return materializePendingCoordinateJobs.execute();
    }

    public EnrichmentBatchResultView enrich(int limit, boolean dryRun, boolean background) {
        return runEnrichmentBatch.execute(limit, dryRun, background);
    }
}
