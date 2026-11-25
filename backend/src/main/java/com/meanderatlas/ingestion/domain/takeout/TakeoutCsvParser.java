package com.meanderatlas.ingestion.domain.takeout;

import com.meanderatlas.ingestion.domain.GoogleCid;
import com.meanderatlas.ingestion.domain.GoogleCidDecoder;
import com.meanderatlas.ingestion.domain.TextNormalizer;
import com.meanderatlas.shared.ListKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

public final class TakeoutCsvParser {

    private static final int NOT_FOUND = -1;
    private static final String CSV_EXTENSION = ".csv";
    private static final Pattern DUPLICATE_SUFFIX_PATTERN = Pattern.compile("\\s*\\(\\d+\\)$");
    private static final Set<String> TITLE_HEADERS = Set.of("title", "tytul");
    private static final Set<String> NOTE_HEADERS = Set.of("note", "notatka");
    private static final Set<String> URL_HEADERS = Set.of("url");
    private static final Set<String> TAGS_HEADERS = Set.of("tags", "tagi");
    private static final Set<String> COMMENT_HEADERS = Set.of("comment", "skomentuj", "komentarz");
    private static final Set<String> NON_MAPS_CSV_BASES =
            Set.of("obrazy", "images", "saved images", "zapisane obrazy", "linki", "links");
    private static final Set<String> WANT_TO_GO_NAMES = Set.of("want to go", "chce odwiedzic");
    private static final Set<String> FAVORITE_NAMES =
            Set.of(
                    "favorite places",
                    "favourite places",
                    "favorites",
                    "favourites",
                    "ulubione miejsca",
                    "ulubione");
    private static final Set<String> STARRED_NAMES =
            Set.of(
                    "starred places",
                    "miejsca oznaczone gwiazdka",
                    "zapisane miejsca",
                    "saved places");
    private static final Pattern GOOGLE_MAPS_URL_PATTERN =
            Pattern.compile(
                    "^https?://(?:(?:www\\.)?google\\.[a-z.]+/maps(?:[/?#].*)?|"
                            + "maps\\.google\\.[a-z.]+(?:[/?#].*)?|"
                            + "maps\\.app\\.goo\\.gl(?:/.*)?|"
                            + "goo\\.gl/maps(?:/.*)?)$",
                    Pattern.CASE_INSENSITIVE);

    private TakeoutCsvParser() {}

    public static List<ParsedSavedCsvRow> parse(Path csvFile) throws IOException {
        Objects.requireNonNull(csvFile, "csvFile must not be null");
        return parse(csvFile.toString(), Files.readString(csvFile, StandardCharsets.UTF_8));
    }

    public static List<ParsedSavedCsvRow> parse(String sourceFile, String content) {
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        Objects.requireNonNull(content, "content must not be null");

        String fileName = extractFileName(sourceFile);
        if (isNonMapsCsv(fileName)) {
            return List.of();
        }

        List<List<String>> records = CsvRecordReader.readRecords(TextNormalizer.stripBom(content));
        String listName = extractListName(fileName);
        ListKind listKind = classifyListKind(fileName);

        return findHeader(records)
                .map(header ->
                        records.stream()
                                .skip(header.rowIndex() + 1L)
                                .flatMap(record ->
                                        parseDataRow(sourceFile, listName, listKind, header, record)
                                                .stream())
                                .toList())
                .orElseGet(List::of);
    }

    public static boolean isNonMapsCsv(String fileName) {
        Objects.requireNonNull(fileName, "fileName must not be null");
        return NON_MAPS_CSV_BASES.contains(canonicalBaseName(fileName));
    }

    public static ListKind classifyListKind(String fileNameOrListName) {
        Objects.requireNonNull(fileNameOrListName, "fileNameOrListName must not be null");
        String canonical = canonicalBaseName(fileNameOrListName);
        if (WANT_TO_GO_NAMES.contains(canonical)) {
            return ListKind.WANT_TO_GO;
        }
        if (FAVORITE_NAMES.contains(canonical)) {
            return ListKind.FAVORITE;
        }
        if (STARRED_NAMES.contains(canonical)) {
            return ListKind.STARRED;
        }
        return ListKind.CUSTOM;
    }

    private static Optional<ParsedSavedCsvRow> parseDataRow(
            String sourceFile,
            String listName,
            ListKind listKind,
            HeaderColumns header,
            List<String> record) {
        String title = TextNormalizer.trimToNull(fieldAt(record, header.titleIndex()));
        String url = TextNormalizer.trimToNull(fieldAt(record, header.urlIndex()));
        if (title == null || url == null || !GOOGLE_MAPS_URL_PATTERN.matcher(url).matches()) {
            return Optional.empty();
        }
        GoogleCid cid = GoogleCidDecoder.decodeFromUrl(url).orElse(null);
        String note = TextNormalizer.trimToNull(fieldAt(record, header.noteIndex()));
        String tags = TextNormalizer.trimToNull(fieldAt(record, header.tagsIndex()));
        String comment = TextNormalizer.trimToNull(fieldAt(record, header.commentIndex()));
        return Optional.of(
                new ParsedSavedCsvRow(
                        sourceFile, listName, listKind, title, url, cid, note, tags, comment));
    }

    private static Optional<HeaderColumns> findHeader(List<List<String>> records) {
        return IntStream.range(0, records.size())
                .mapToObj(rowIndex -> matchHeader(rowIndex, records.get(rowIndex)))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<HeaderColumns> matchHeader(int rowIndex, List<String> row) {
        List<String> normalized = row.stream().map(TextNormalizer::normalizeFolded).toList();
        int titleIndex = findColumn(normalized, TITLE_HEADERS);
        int urlIndex = findColumn(normalized, URL_HEADERS);
        if (titleIndex == NOT_FOUND || urlIndex == NOT_FOUND) {
            return Optional.empty();
        }
        return Optional.of(
                new HeaderColumns(
                        rowIndex,
                        titleIndex,
                        urlIndex,
                        findColumn(normalized, NOTE_HEADERS),
                        findColumn(normalized, TAGS_HEADERS),
                        findColumn(normalized, COMMENT_HEADERS)));
    }

    private static int findColumn(List<String> normalizedRow, Set<String> candidates) {
        return IntStream.range(0, normalizedRow.size())
                .filter(col -> candidates.contains(normalizedRow.get(col)))
                .findFirst()
                .orElse(NOT_FOUND);
    }

    private static String extractFileName(String sourceFile) {
        String normalizedPath = sourceFile.replace('\\', '/');
        int lastSlash = normalizedPath.lastIndexOf('/');
        return lastSlash >= 0 ? normalizedPath.substring(lastSlash + 1) : normalizedPath;
    }

    private static String extractListName(String fileName) {
        String base = extractFileName(fileName);
        if (base.toLowerCase(Locale.ROOT).endsWith(CSV_EXTENSION)) {
            base = base.substring(0, base.length() - CSV_EXTENSION.length());
        }
        return TextNormalizer.normalizeSpaces(base);
    }

    private static String canonicalBaseName(String fileNameOrListName) {
        String folded = TextNormalizer.normalizeFolded(extractListName(fileNameOrListName));
        return DUPLICATE_SUFFIX_PATTERN.matcher(folded).replaceFirst("").trim();
    }

    private static @Nullable String fieldAt(List<String> row, int index) {
        return index >= 0 && index < row.size() ? row.get(index) : null;
    }

    private record HeaderColumns(
            int rowIndex,
            int titleIndex,
            int urlIndex,
            int noteIndex,
            int tagsIndex,
            int commentIndex) {}
}
