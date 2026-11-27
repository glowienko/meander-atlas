package com.meanderatlas.ingestion;

import com.meanderatlas.ingestion.application.GetImportStatus;
import com.meanderatlas.ingestion.application.ImportLocalDirectory;
import com.meanderatlas.ingestion.application.MaterializePendingCoordinateJobs;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class ImportFacade {

    private final ImportLocalDirectory importLocalDirectory;
    private final GetImportStatus getImportStatus;
    private final MaterializePendingCoordinateJobs materializePendingCoordinateJobs;

    public ImportFacade(
            ImportLocalDirectory importLocalDirectory,
            GetImportStatus getImportStatus,
            MaterializePendingCoordinateJobs materializePendingCoordinateJobs) {
        this.importLocalDirectory = importLocalDirectory;
        this.getImportStatus = getImportStatus;
        this.materializePendingCoordinateJobs = materializePendingCoordinateJobs;
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
}
