package com.meanderatlas.ingestion.domain.source;

import java.util.List;

public record AccountImportSource(
        String accountLabel, String contentSha256, List<DiscoveredImportFile> files) {}
