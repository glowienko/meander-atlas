package com.meanderatlas.ingestion;

import com.meanderatlas.ingestion.application.GetImportStatus;
import com.meanderatlas.ingestion.application.ImportLocalDirectory;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class ImportFacade {

    private final ImportLocalDirectory importLocalDirectory;
    private final GetImportStatus getImportStatus;

    public ImportFacade(
            ImportLocalDirectory importLocalDirectory, GetImportStatus getImportStatus) {
        this.importLocalDirectory = importLocalDirectory;
        this.getImportStatus = getImportStatus;
    }

    public LocalImportSummaryView importLocalDirectory(@Nullable String account, boolean force) {
        return importLocalDirectory.execute(account, force);
    }

    public ImportStatusView status() {
        return getImportStatus.execute();
    }
}
