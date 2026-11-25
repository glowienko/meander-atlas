package com.meanderatlas.ingestion.adapter.web;

import com.meanderatlas.ingestion.ImportFacade;
import com.meanderatlas.ingestion.ImportStatusView;
import com.meanderatlas.ingestion.LocalImportSummaryView;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/imports")
class ImportController {

    private final ImportFacade importFacade;

    ImportController(ImportFacade importFacade) {
        this.importFacade = importFacade;
    }

    @PostMapping(path = "/local", produces = MediaType.APPLICATION_JSON_VALUE)
    LocalImportSummaryView importLocal(
            @RequestParam(required = false) @Nullable String account,
            @RequestParam(defaultValue = "false") boolean force) {
        return importFacade.importLocalDirectory(account, force);
    }

    @GetMapping(path = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    ImportStatusView status() {
        return importFacade.status();
    }
}
