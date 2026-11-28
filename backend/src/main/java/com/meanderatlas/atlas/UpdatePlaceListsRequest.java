package com.meanderatlas.atlas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdatePlaceListsRequest(
        @NotNull @Size(max = 50) List<@Valid @NotNull PlaceListEntryRequest> lists) {}
