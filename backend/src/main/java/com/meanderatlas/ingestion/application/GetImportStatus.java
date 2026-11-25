package com.meanderatlas.ingestion.application;

import com.meanderatlas.ingestion.ImportStatusView;
import com.meanderatlas.ingestion.domain.source.ImportRepository;
import com.meanderatlas.ingestion.domain.source.ImportStatusSummary;
import com.meanderatlas.shared.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetImportStatus {

    private final ImportRepository importRepository;
    private final CurrentUserProvider currentUserProvider;

    public GetImportStatus(
            ImportRepository importRepository, CurrentUserProvider currentUserProvider) {
        this.importRepository = importRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public ImportStatusView execute() {
        ImportStatusSummary summary =
                importRepository.fetchStatus(currentUserProvider.currentUserId());
        return new ImportStatusView(
                summary.pendingResolutionCount(),
                summary.completedBatches(),
                summary.accounts(),
                summary.lastImportedAt());
    }
}
