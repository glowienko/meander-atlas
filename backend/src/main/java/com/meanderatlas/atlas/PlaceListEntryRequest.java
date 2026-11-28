package com.meanderatlas.atlas;

import com.meanderatlas.shared.ListKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record PlaceListEntryRequest(
        @NotBlank @Size(max = 160) String listName,
        @Nullable ListKind listKind,
        @Nullable @Size(max = 120) String accountLabel) {}
