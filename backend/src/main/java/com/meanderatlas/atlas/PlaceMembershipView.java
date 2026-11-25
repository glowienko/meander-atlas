package com.meanderatlas.atlas;

import com.meanderatlas.shared.ListKind;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record PlaceMembershipView(
        @NotNull String accountLabel,
        @NotNull String listName,
        @NotNull ListKind listKind,
        @Nullable String note,
        @Nullable String tags,
        @Nullable String sourceUrl,
        @Nullable Instant savedAt) {}
