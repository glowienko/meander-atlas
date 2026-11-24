package com.meanderatlas.ingestion.domain;

import com.meanderatlas.atlas.ListKind;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record ParsedSavedCsvRow(
        String sourceFile,
        String listName,
        ListKind listKind,
        String title,
        String url,
        @Nullable GoogleCid googleCid,
        @Nullable String note,
        @Nullable String tags,
        @Nullable String comment) {

    private static final String NULL_SOURCE_FILE_MESSAGE = "sourceFile must not be null";
    private static final String NULL_LIST_NAME_MESSAGE = "listName must not be null";
    private static final String NULL_LIST_KIND_MESSAGE = "listKind must not be null";
    private static final String NULL_TITLE_MESSAGE = "title must not be null";
    private static final String NULL_URL_MESSAGE = "url must not be null";

    public ParsedSavedCsvRow {
        Objects.requireNonNull(sourceFile, NULL_SOURCE_FILE_MESSAGE);
        Objects.requireNonNull(listName, NULL_LIST_NAME_MESSAGE);
        Objects.requireNonNull(listKind, NULL_LIST_KIND_MESSAGE);
        Objects.requireNonNull(title, NULL_TITLE_MESSAGE);
        Objects.requireNonNull(url, NULL_URL_MESSAGE);
    }
}
