package com.meanderatlas.ingestion.domain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class FixtureArchiveSupport {

    private static final String FIXTURES_DIR = "../fixtures";
    private static final String TAKEOUT_ZIP_NAME = "synthetic-takeout-pl-en.zip";
    private static final String TIMELINE_JSON_NAME = "synthetic-timeline.json";
    private static final String ZIP_SLIP_MESSAGE = "Zip entry escapes target directory: ";

    private FixtureArchiveSupport() {}

    static Path extractSyntheticTakeout(Path targetDir) throws IOException {
        Path zipPath = fixturePath(TAKEOUT_ZIP_NAME);
        Path normalizedRoot = targetDir.toAbsolutePath().normalize();
        try (InputStream in = Files.newInputStream(zipPath);
                ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path destination = normalizedRoot.resolve(entry.getName()).normalize();
                if (!destination.startsWith(normalizedRoot)) {
                    throw new IOException(ZIP_SLIP_MESSAGE + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Path parent = destination.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(zis, destination);
                }
                zis.closeEntry();
            }
        }
        return normalizedRoot;
    }

    static Path syntheticTimelinePath() {
        return fixturePath(TIMELINE_JSON_NAME);
    }

    private static Path fixturePath(String fileName) {
        return Path.of(FIXTURES_DIR, fileName).toAbsolutePath().normalize();
    }
}
