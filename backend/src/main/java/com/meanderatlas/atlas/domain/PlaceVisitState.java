package com.meanderatlas.atlas.domain;

import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record PlaceVisitState(
        PlaceId placeId,
        UserId userId,
        VisitStatus status,
        @Nullable VisitSource source,
        int visitCount,
        @Nullable Instant firstVisitedAt,
        @Nullable Instant lastVisitedAt) {

    private static final int ZERO_VISITS = 0;
    private static final int SINGLE_VISIT = 1;

    private static final String NULL_PLACE_ID_MESSAGE = "placeId must not be null";
    private static final String NULL_USER_ID_MESSAGE = "userId must not be null";
    private static final String NULL_STATUS_MESSAGE = "status must not be null";
    private static final String NEGATIVE_VISIT_COUNT_MESSAGE = "visitCount must not be negative";
    private static final String MISSING_SOURCE_MESSAGE =
            "source is required when status is VISITED or NEEDS_TRIAGE";
    private static final String INVALID_VISITED_COUNT_MESSAGE =
            "visitCount must be at least 1 when status is VISITED";
    private static final String INVALID_UNVISITED_STATE_MESSAGE =
            "UNVISITED state must have zero visits and no visit timestamps";
    private static final String SNAPSHOT_IDENTITY_MISMATCH_MESSAGE =
            "Snapshot placeId and userId must match current visit state";

    public PlaceVisitState {
        Objects.requireNonNull(placeId, NULL_PLACE_ID_MESSAGE);
        Objects.requireNonNull(userId, NULL_USER_ID_MESSAGE);
        Objects.requireNonNull(status, NULL_STATUS_MESSAGE);
        if (visitCount < ZERO_VISITS) {
            throw new IllegalArgumentException(NEGATIVE_VISIT_COUNT_MESSAGE);
        }
        if (status != VisitStatus.UNVISITED && source == null) {
            throw new IllegalArgumentException(MISSING_SOURCE_MESSAGE);
        }
        if (status == VisitStatus.VISITED && visitCount < SINGLE_VISIT) {
            throw new IllegalArgumentException(INVALID_VISITED_COUNT_MESSAGE);
        }
        if (status == VisitStatus.UNVISITED
                && (visitCount != ZERO_VISITS || firstVisitedAt != null || lastVisitedAt != null)) {
            throw new IllegalArgumentException(INVALID_UNVISITED_STATE_MESSAGE);
        }
    }

    public static PlaceVisitState unvisited(PlaceId placeId, UserId userId) {
        return new PlaceVisitState(
                placeId, userId, VisitStatus.UNVISITED, null, ZERO_VISITS, null, null);
    }

    public PlaceVisitState apply(
            VisitStatus incomingStatus, VisitSource incomingSource, @Nullable Instant observedAt) {
        Objects.requireNonNull(incomingStatus, NULL_STATUS_MESSAGE);
        Objects.requireNonNull(incomingSource, MISSING_SOURCE_MESSAGE);

        if (source != null && source.outranks(incomingSource)) {
            return this;
        }
        if (incomingStatus == VisitStatus.NEEDS_TRIAGE && status == VisitStatus.VISITED) {
            return this;
        }

        return switch (incomingStatus) {
            case UNVISITED ->
                    new PlaceVisitState(
                            placeId,
                            userId,
                            VisitStatus.UNVISITED,
                            incomingSource,
                            ZERO_VISITS,
                            null,
                            null);
            case NEEDS_TRIAGE ->
                    new PlaceVisitState(
                            placeId,
                            userId,
                            VisitStatus.NEEDS_TRIAGE,
                            incomingSource,
                            ZERO_VISITS,
                            null,
                            null);
            case VISITED -> applyVisited(incomingSource, observedAt);
        };
    }

    public PlaceVisitState restoreFrom(PlaceVisitState snapshot) {
        Objects.requireNonNull(snapshot, SNAPSHOT_IDENTITY_MISMATCH_MESSAGE);
        if (!placeId.equals(snapshot.placeId) || !userId.equals(snapshot.userId)) {
            throw new IllegalArgumentException(SNAPSHOT_IDENTITY_MISMATCH_MESSAGE);
        }
        return snapshot;
    }

    private PlaceVisitState applyVisited(VisitSource incomingSource, @Nullable Instant observedAt) {
        int nextCount =
                incomingSource.isManual()
                        ? Math.max(SINGLE_VISIT, visitCount)
                        : visitCount + SINGLE_VISIT;
        Instant nextFirst = earliest(firstVisitedAt, observedAt);
        Instant nextLast = latest(lastVisitedAt, observedAt);
        return new PlaceVisitState(
                placeId,
                userId,
                VisitStatus.VISITED,
                incomingSource,
                nextCount,
                nextFirst,
                nextLast);
    }

    private static @Nullable Instant earliest(
            @Nullable Instant current, @Nullable Instant candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null) {
            return current;
        }
        return candidate.isBefore(current) ? candidate : current;
    }

    private static @Nullable Instant latest(
            @Nullable Instant current, @Nullable Instant candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null) {
            return current;
        }
        return candidate.isAfter(current) ? candidate : current;
    }
}
