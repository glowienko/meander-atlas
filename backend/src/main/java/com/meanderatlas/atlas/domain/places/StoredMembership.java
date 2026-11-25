package com.meanderatlas.atlas.domain.places;

import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record StoredMembership(
        PlaceId placeId,
        String accountLabel,
        String listName,
        ListKind listKind,
        @Nullable String note,
        @Nullable String tags,
        @Nullable String sourceUrl,
        @Nullable Instant savedAt) {}
