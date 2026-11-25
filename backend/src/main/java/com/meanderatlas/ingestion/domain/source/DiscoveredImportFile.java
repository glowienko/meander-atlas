package com.meanderatlas.ingestion.domain.source;

import java.nio.file.Path;

public record DiscoveredImportFile(String relativePath, ImportFileKind kind, Path absolutePath) {}
