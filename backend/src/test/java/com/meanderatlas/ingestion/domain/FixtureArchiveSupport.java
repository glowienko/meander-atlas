package com.meanderatlas.ingestion.domain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class FixtureArchiveSupport {

    private static final String FIXTURES_DIR = "../fixtures";

    private FixtureArchiveSupport() {}

    public static Path extractSyntheticTakeout(Path targetDir) throws IOException {
        Path zipPath = fixturePath("synthetic-takeout-pl-en.zip");
        Path normalizedRoot = targetDir.toAbsolutePath().normalize();
        try (InputStream in = Files.newInputStream(zipPath);
                ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path destination = normalizedRoot.resolve(entry.getName()).normalize();
                if (!destination.startsWith(normalizedRoot)) {
                    throw new IOException("Zip entry escapes target directory: " + entry.getName());
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

    public static Path syntheticTimelinePath() {
        return fixturePath("synthetic-timeline.json");
    }

    private static Path fixturePath(String fileName) {
        return Path.of(FIXTURES_DIR, fileName).toAbsolutePath().normalize();
    }
}
