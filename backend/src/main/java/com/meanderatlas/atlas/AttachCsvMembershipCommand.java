package com.meanderatlas.atlas;

import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record AttachCsvMembershipCommand(
        long googleCid,
        String canonicalName,
        PlaceCategory category,
        @Nullable String subcategory,
        CategorySource categorySource,
        String accountLabel,
        String listName,
        ListKind listKind,
        @Nullable String note,
        @Nullable String tags,
        @Nullable String sourceUrl,
        @Nullable Instant savedAt,
        @Nullable VisitStatus initialVisitStatus,
        @Nullable VisitSource initialVisitSource,
        @Nullable Instant observedAt) {

    public AttachCsvMembershipCommand {
        Objects.requireNonNull(canonicalName, "canonicalName must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(categorySource, "categorySource must not be null");
        Objects.requireNonNull(accountLabel, "accountLabel must not be null");
        Objects.requireNonNull(listName, "listName must not be null");
        Objects.requireNonNull(listKind, "listKind must not be null");
    }
}
