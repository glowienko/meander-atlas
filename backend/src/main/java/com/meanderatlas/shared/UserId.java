package com.meanderatlas.shared;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {

    private static final String NULL_VALUE_MESSAGE = "UserId value must not be null";

    public UserId {
        Objects.requireNonNull(value, NULL_VALUE_MESSAGE);
    }

    public static UserId fromString(String raw) {
        return new UserId(UUID.fromString(raw));
    }
}
