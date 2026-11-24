package com.meanderatlas.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VisitSourceTest {

    @Test
    void manualSourcesOutrankReviewAndTimeline() {
        assertThat(VisitSource.MANUAL_SINGLE.isManual()).isTrue();
        assertThat(VisitSource.MANUAL_BULK.isManual()).isTrue();
        assertThat(VisitSource.REVIEW.isManual()).isFalse();
        assertThat(VisitSource.TIMELINE.isManual()).isFalse();

        assertThat(VisitSource.MANUAL_SINGLE.outranks(VisitSource.REVIEW)).isTrue();
        assertThat(VisitSource.MANUAL_BULK.outranks(VisitSource.TIMELINE)).isTrue();
        assertThat(VisitSource.MANUAL_SINGLE.outranks(VisitSource.MANUAL_BULK)).isFalse();
        assertThat(VisitSource.REVIEW.outranks(VisitSource.TIMELINE)).isTrue();
        assertThat(VisitSource.TIMELINE.outranks(VisitSource.REVIEW)).isFalse();
        assertThat(VisitSource.REVIEW.outranks(VisitSource.MANUAL_SINGLE)).isFalse();
    }
}
