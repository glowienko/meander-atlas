package com.meanderatlas.shared;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "UserId value must not be null");
    }

    public static UserId fromString(String raw) {
        return new UserId(UUID.fromString(raw));
    }
}
