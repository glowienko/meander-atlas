package com.meanderatlas.ingestion.adapter.persistence;

import com.meanderatlas.ingestion.ImportFacade;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.ingestion.startup-sweep",
        havingValue = "true",
        matchIfMissing = true)
class IngestionStartupRunner implements ApplicationRunner {

    private final ImportFacade importFacade;

    IngestionStartupRunner(ImportFacade importFacade) {
        this.importFacade = importFacade;
    }

    @Override
    public void run(ApplicationArguments args) {
        importFacade.runStartupSweep();
    }
}
