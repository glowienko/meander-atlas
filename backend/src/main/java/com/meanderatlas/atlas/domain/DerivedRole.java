package com.meanderatlas.atlas.domain;

public enum DerivedRole {
    PREFERRED_HOTEL(DerivedRole.PREFERRED_HOTEL_PRIORITY),
    MUST_SEE(DerivedRole.MUST_SEE_PRIORITY),
    FAVORITE(DerivedRole.FAVORITE_PRIORITY),
    BACKLOG(DerivedRole.BACKLOG_PRIORITY);

    private static final int PREFERRED_HOTEL_PRIORITY = 40;
    private static final int MUST_SEE_PRIORITY = 30;
    private static final int FAVORITE_PRIORITY = 20;
    private static final int BACKLOG_PRIORITY = 10;

    private final int defaultPriority;

    DerivedRole(int defaultPriority) {
        this.defaultPriority = defaultPriority;
    }

    public int defaultPriority() {
        return defaultPriority;
    }
}
