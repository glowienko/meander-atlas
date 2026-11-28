package com.meanderatlas.atlas.domain.places;

import com.meanderatlas.shared.ListKind;
import java.util.Objects;

public record PlaceListMembershipTarget(String accountLabel, String listName, ListKind listKind) {

    public PlaceListMembershipTarget {
        Objects.requireNonNull(accountLabel, "accountLabel must not be null");
        Objects.requireNonNull(listName, "listName must not be null");
        Objects.requireNonNull(listKind, "listKind must not be null");
    }
}
