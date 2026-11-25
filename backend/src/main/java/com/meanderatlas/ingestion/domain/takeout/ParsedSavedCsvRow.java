package com.meanderatlas.ingestion.domain.takeout;

import com.meanderatlas.ingestion.domain.GoogleCid;
import com.meanderatlas.shared.ListKind;
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

    public ParsedSavedCsvRow {
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        Objects.requireNonNull(listName, "listName must not be null");
        Objects.requireNonNull(listKind, "listKind must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(url, "url must not be null");
    }
}
