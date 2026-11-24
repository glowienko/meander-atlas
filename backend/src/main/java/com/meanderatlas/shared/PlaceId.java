package com.meanderatlas.shared;

import java.util.Objects;
import java.util.UUID;

public record PlaceId(UUID value) {

    private static final String NULL_VALUE_MESSAGE = "PlaceId value must not be null";

    public PlaceId {
        Objects.requireNonNull(value, NULL_VALUE_MESSAGE);
    }

    public static PlaceId random() {
        return new PlaceId(UUID.randomUUID());
    }
}
