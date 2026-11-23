package com.meanderatlas.shared;

import java.util.Objects;
import java.util.UUID;

public record PlaceId(UUID value) {

    public PlaceId {
        Objects.requireNonNull(value, "PlaceId value must not be null");
    }

    public static PlaceId random() {
        return new PlaceId(UUID.randomUUID());
    }
}
