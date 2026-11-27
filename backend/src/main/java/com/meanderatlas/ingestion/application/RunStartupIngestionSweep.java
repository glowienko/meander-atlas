package com.meanderatlas.ingestion.application;

import com.meanderatlas.ingestion.domain.source.EnrichmentJobRepository;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RunStartupIngestionSweep {

    private static final Logger LOG = LoggerFactory.getLogger(RunStartupIngestionSweep.class);

    private final EnrichmentJobRepository enrichmentJobRepository;
    private final MaterializePendingCoordinateJobs materializePendingCoordinateJobs;
    private final RunEnrichmentBatch runEnrichmentBatch;
    private final CurrentUserProvider currentUserProvider;

    public RunStartupIngestionSweep(
            EnrichmentJobRepository enrichmentJobRepository,
            MaterializePendingCoordinateJobs materializePendingCoordinateJobs,
            RunEnrichmentBatch runEnrichmentBatch,
            CurrentUserProvider currentUserProvider) {
        this.enrichmentJobRepository = enrichmentJobRepository;
        this.materializePendingCoordinateJobs = materializePendingCoordinateJobs;
        this.runEnrichmentBatch = runEnrichmentBatch;
        this.currentUserProvider = currentUserProvider;
    }

    public void execute() {
        UserId userId = currentUserProvider.currentUserId();
        int pendingLookups = enrichmentJobRepository.countPendingCoordinateLookups(userId);
        if (pendingLookups > 0) {
            LOG.info(
                    "Startup sweep found {} legacy pending coordinate lookups; materializing offline",
                    pendingLookups);
            materializePendingCoordinateJobs.execute();
        }
        boolean started = runEnrichmentBatch.startBackgroundDrainIfEnabled();
        if (started) {
            LOG.info("Startup sweep resumed background open-catalog enrichment");
        }
    }
}
