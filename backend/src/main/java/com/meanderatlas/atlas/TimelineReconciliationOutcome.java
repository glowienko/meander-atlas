package com.meanderatlas.atlas;

public enum TimelineReconciliationOutcome {
    MATCHED_VISITED,
    MARKED_NEEDS_TRIAGE,
    SKIPPED_MANUAL_OVERRIDE,
    UNMATCHED
}
