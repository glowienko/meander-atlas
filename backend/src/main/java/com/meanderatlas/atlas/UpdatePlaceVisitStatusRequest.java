package com.meanderatlas.atlas;

import com.meanderatlas.shared.VisitStatus;
import jakarta.validation.constraints.NotNull;

public record UpdatePlaceVisitStatusRequest(@NotNull VisitStatus status) {}
