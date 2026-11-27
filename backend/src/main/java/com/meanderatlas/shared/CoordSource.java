package com.meanderatlas.shared;

public enum CoordSource {
    TAKEOUT,
    TAKEOUT_S2_APPROX,
    TIMELINE,
    OPEN_CATALOG,
    GOOGLE_TEMP;

    public boolean isExactAnchor() {
        return this == TAKEOUT || this == TIMELINE;
    }
}
