package com.meanderatlas.atlas;

import com.meanderatlas.shared.VisitStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record BulkVisitStatusRequest(
        @NotEmpty @Size(max = 500) List<@NotNull UUID> placeIds,
        @NotNull VisitStatus targetStatus) {}
