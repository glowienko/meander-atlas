package com.meanderatlas.shared;

import java.util.Objects;

public enum VisitSource {
    TIMELINE,
    REVIEW,
    MANUAL_SINGLE,
    MANUAL_BULK;

    private static final String NULL_OTHER_MESSAGE = "other VisitSource must not be null";

    public boolean isManual() {
        return this == MANUAL_SINGLE || this == MANUAL_BULK;
    }

    public boolean outranks(VisitSource other) {
        Objects.requireNonNull(other, NULL_OTHER_MESSAGE);
        if (isManual()) {
            return !other.isManual();
        }
        return this == REVIEW && other == TIMELINE;
    }
}
