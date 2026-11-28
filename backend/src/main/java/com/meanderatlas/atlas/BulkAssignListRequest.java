package com.meanderatlas.atlas;

import com.meanderatlas.shared.ListKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record BulkAssignListRequest(
        @NotEmpty @Size(max = 500) List<@NotNull UUID> placeIds,
        @NotBlank @Size(max = 160) String targetListName,
        @Nullable ListKind listKind,
        @NotNull ListAssignMode mode,
        @Nullable @Size(max = 160) String sourceListName,
        @Nullable @Size(max = 120) String accountLabel) {}
