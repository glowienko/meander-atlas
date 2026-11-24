package com.meanderatlas.ingestion.domain;

import com.meanderatlas.atlas.ListKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class TakeoutCsvParser {

    private static final char COMMA = ',';
    private static final char QUOTE = '"';
    private static final char CARRIAGE_RETURN = '\r';
    private static final char LINE_FEED = '\n';
    private static final int NOT_FOUND = -1;
    private static final String CSV_EXTENSION = ".csv";
    private static final String NULL_PATH_MESSAGE = "csvFile must not be null";
    private static final String NULL_SOURCE_MESSAGE = "sourceFile must not be null";
    private static final String NULL_CONTENT_MESSAGE = "content must not be null";
    private static final String NULL_FILENAME_MESSAGE = "fileName must not be null";
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
        Objects.requireNonNull(csvFile, NULL_PATH_MESSAGE);
        String content = Files.readString(csvFile, StandardCharsets.UTF_8);
        return parse(csvFile.toString(), content);
    }

    public static List<ParsedSavedCsvRow> parse(String sourceFile, String content) {
        Objects.requireNonNull(sourceFile, NULL_SOURCE_MESSAGE);
        Objects.requireNonNull(content, NULL_CONTENT_MESSAGE);

        String fileName = extractFileName(sourceFile);
        if (isNonMapsCsv(fileName)) {
            return List.of();
        }

        String cleaned = TextNormalizer.stripBom(content);
        List<List<String>> records = readCsvRecords(cleaned);
        HeaderColumns header = findHeader(records);
        if (header == null) {
            return List.of();
        }

        String listName = extractListName(fileName);
        ListKind listKind = classifyListKind(fileName);
        List<ParsedSavedCsvRow> rows = new ArrayList<>();
        for (int i = header.rowIndex() + 1; i < records.size(); i++) {
            List<String> record = records.get(i);
            String title = TextNormalizer.trimToNull(fieldAt(record, header.titleIndex()));
            String url = TextNormalizer.trimToNull(fieldAt(record, header.urlIndex()));
            if (title == null || url == null || !isGoogleMapsUrl(url)) {
                continue;
            }
            GoogleCid cid = GoogleCidDecoder.decodeFromUrl(url).orElse(null);
            String note = TextNormalizer.trimToNull(fieldAt(record, header.noteIndex()));
            String tags = TextNormalizer.trimToNull(fieldAt(record, header.tagsIndex()));
            String comment = TextNormalizer.trimToNull(fieldAt(record, header.commentIndex()));
            rows.add(
                    new ParsedSavedCsvRow(
                            sourceFile, listName, listKind, title, url, cid, note, tags, comment));
        }
        return List.copyOf(rows);
    }

    public static boolean isNonMapsCsv(String fileName) {
        Objects.requireNonNull(fileName, NULL_FILENAME_MESSAGE);
        String canonical = canonicalBaseName(fileName);
        return NON_MAPS_CSV_BASES.contains(canonical);
    }

    public static ListKind classifyListKind(String fileNameOrListName) {
        Objects.requireNonNull(fileNameOrListName, NULL_FILENAME_MESSAGE);
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

    private static boolean isGoogleMapsUrl(String url) {
        return GOOGLE_MAPS_URL_PATTERN.matcher(url).matches();
    }

    private static @Nullable HeaderColumns findHeader(List<List<String>> records) {
        for (int rowIndex = 0; rowIndex < records.size(); rowIndex++) {
            List<String> row = records.get(rowIndex);
            int titleIndex = NOT_FOUND;
            int urlIndex = NOT_FOUND;
            int noteIndex = NOT_FOUND;
            int tagsIndex = NOT_FOUND;
            int commentIndex = NOT_FOUND;

            for (int col = 0; col < row.size(); col++) {
                String normalized = TextNormalizer.normalizeFolded(row.get(col));
                if (titleIndex == NOT_FOUND && TITLE_HEADERS.contains(normalized)) {
                    titleIndex = col;
                } else if (urlIndex == NOT_FOUND && URL_HEADERS.contains(normalized)) {
                    urlIndex = col;
                } else if (noteIndex == NOT_FOUND && NOTE_HEADERS.contains(normalized)) {
                    noteIndex = col;
                } else if (tagsIndex == NOT_FOUND && TAGS_HEADERS.contains(normalized)) {
                    tagsIndex = col;
                } else if (commentIndex == NOT_FOUND && COMMENT_HEADERS.contains(normalized)) {
                    commentIndex = col;
                }
            }
            if (titleIndex != NOT_FOUND && urlIndex != NOT_FOUND) {
                return new HeaderColumns(
                        rowIndex, titleIndex, urlIndex, noteIndex, tagsIndex, commentIndex);
            }
        }
        return null;
    }

    private static @Nullable String fieldAt(List<String> row, int index) {
        if (index < 0 || index >= row.size()) {
            return null;
        }
        return row.get(index);
    }

    private static List<List<String>> readCsvRecords(String content) {
        List<List<String>> records = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        int length = content.length();

        for (int i = 0; i < length; i++) {
            char ch = content.charAt(i);
            if (inQuotes) {
                if (ch == QUOTE) {
                    if (i + 1 < length && content.charAt(i + 1) == QUOTE) {
                        currentField.append(QUOTE);
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    currentField.append(ch);
                }
            } else if (ch == QUOTE && currentField.isEmpty()) {
                inQuotes = true;
            } else if (ch == COMMA) {
                currentRow.add(currentField.toString());
                currentField.setLength(0);
            } else if (ch == CARRIAGE_RETURN || ch == LINE_FEED) {
                if (ch == CARRIAGE_RETURN && i + 1 < length && content.charAt(i + 1) == LINE_FEED) {
                    i++;
                }
                currentRow.add(currentField.toString());
                currentField.setLength(0);
                records.add(currentRow);
                currentRow = new ArrayList<>();
            } else {
                currentField.append(ch);
            }
        }
        if (!currentField.isEmpty() || !currentRow.isEmpty()) {
            currentRow.add(currentField.toString());
            records.add(currentRow);
        }
        return records;
    }

    private record HeaderColumns(
            int rowIndex,
            int titleIndex,
            int urlIndex,
            int noteIndex,
            int tagsIndex,
            int commentIndex) {}
}
