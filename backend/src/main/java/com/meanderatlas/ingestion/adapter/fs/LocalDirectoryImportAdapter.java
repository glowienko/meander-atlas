package com.meanderatlas.ingestion.adapter.fs;

import com.meanderatlas.ingestion.domain.TextNormalizer;
import com.meanderatlas.ingestion.domain.source.AccountImportSource;
import com.meanderatlas.ingestion.domain.source.DiscoveredImportFile;
import com.meanderatlas.ingestion.domain.source.ImportFileKind;
import com.meanderatlas.ingestion.domain.source.TripImportSourcePort;
import com.meanderatlas.ingestion.domain.takeout.TakeoutCsvParser;
import com.meanderatlas.ingestion.domain.takeout.TakeoutGeoJsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class LocalDirectoryImportAdapter implements TripImportSourcePort {

    private static final int BUFFER_SIZE = 8192;
    private static final Set<String> IGNORED_JSON_NAMES =
            Set.of("oznaczone miejsca", "labeled places");

    private final Path importsDir;

    LocalDirectoryImportAdapter(
            @Value("${app.imports-dir:${user.home}/meander-atlas-data/imports}")
                    String importsDir) {
        this.importsDir = Path.of(importsDir).toAbsolutePath().normalize();
    }

    @Override
    public List<AccountImportSource> discoverAccounts(@Nullable String accountFilter) {
        if (!Files.isDirectory(importsDir)) {
            return List.of();
        }
        String normalizedFilter = TextNormalizer.trimToNull(accountFilter);
        try (Stream<Path> accountDirs = Files.list(importsDir)) {
            return accountDirs
                    .filter(Files::isDirectory)
                    .filter(dir -> !isHidden(dir))
                    .filter(dir ->
                            normalizedFilter == null
                                    || dir.getFileName().toString().equals(normalizedFilter))
                    .sorted(Comparator.comparing(dir -> dir.getFileName().toString()))
                    .map(this::scanAccountDirectory)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to list imports directory: " + importsDir, ex);
        }
    }

    private Optional<AccountImportSource> scanAccountDirectory(Path accountDir) {
        String accountLabel = accountDir.getFileName().toString();
        try (Stream<Path> walk = Files.walk(accountDir)) {
            List<DiscoveredImportFile> files =
                    walk.filter(Files::isRegularFile)
                            .filter(path -> !isHidden(path))
                            .map(path -> classifyFile(accountDir, path))
                            .flatMap(Optional::stream)
                            .sorted(Comparator.comparing(DiscoveredImportFile::relativePath))
                            .toList();
            if (files.isEmpty()) {
                return Optional.empty();
            }
            String sha256 = computeSha256(files);
            return Optional.of(new AccountImportSource(accountLabel, sha256, files));
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to scan account directory: " + accountDir, ex);
        }
    }

    private static Optional<DiscoveredImportFile> classifyFile(Path accountDir, Path file) {
        String fileName = file.getFileName().toString();
        String relativePath = accountDir.relativize(file).toString().replace('\\', '/');
        return detectFileKind(fileName)
                .map(kind -> new DiscoveredImportFile(relativePath, kind, file));
    }

    private static Optional<ImportFileKind> detectFileKind(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        int dotIndex = lower.lastIndexOf('.');
        if (dotIndex < 0) {
            return Optional.empty();
        }
        return switch (lower.substring(dotIndex + 1)) {
            case "csv" ->
                    TakeoutCsvParser.isNonMapsCsv(fileName)
                            ? Optional.empty()
                            : Optional.of(ImportFileKind.SAVED_CSV);
            case "json" -> classifyJson(fileName, lower);
            default -> Optional.empty();
        };
    }

    private static Optional<ImportFileKind> classifyJson(String fileName, String lowerFileName) {
        String baseName =
                TextNormalizer.normalizeFolded(
                        lowerFileName.substring(0, lowerFileName.length() - ".json".length()));
        if (IGNORED_JSON_NAMES.contains(baseName)) {
            return Optional.empty();
        }
        if (TakeoutGeoJsonParser.isSavedPlacesFile(fileName)) {
            return Optional.of(ImportFileKind.STARRED_GEOJSON);
        }
        if (TakeoutGeoJsonParser.isReviewsFile(fileName)) {
            return Optional.of(ImportFileKind.REVIEW_GEOJSON);
        }
        return "timeline".equals(baseName)
                ? Optional.of(ImportFileKind.TIMELINE_JSON)
                : Optional.empty();
    }

    private static boolean isHidden(Path path) {
        Path fileName = path.getFileName();
        return fileName != null && fileName.toString().startsWith(".");
    }

    private static String computeSha256(List<DiscoveredImportFile> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            for (DiscoveredImportFile file : files) {
                digest.update(file.relativePath().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                try (InputStream in = Files.newInputStream(file.absolutePath())) {
                    int read = in.read(buffer);
                    while (read != -1) {
                        digest.update(buffer, 0, read);
                        read = in.read(buffer);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to hash import files", ex);
        }
    }
}
