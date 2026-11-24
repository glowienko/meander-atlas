package com.meanderatlas.atlas.domain;

import java.util.Collection;
import java.util.Objects;

public final class DerivedRoleResolver {

    private static final String NULL_CATEGORY_MESSAGE = "category must not be null";
    private static final String NULL_LIST_KINDS_MESSAGE = "listKinds must not be null";

    private DerivedRoleResolver() {}

    public static DerivedRole resolve(PlaceCategory category, Collection<ListKind> listKinds) {
        Objects.requireNonNull(category, NULL_CATEGORY_MESSAGE);
        Objects.requireNonNull(listKinds, NULL_LIST_KINDS_MESSAGE);

        if (listKinds.contains(ListKind.STARRED)) {
            return category == PlaceCategory.HOTEL
                    ? DerivedRole.PREFERRED_HOTEL
                    : DerivedRole.MUST_SEE;
        }
        if (listKinds.contains(ListKind.FAVORITE)) {
            return DerivedRole.FAVORITE;
        }
        return DerivedRole.BACKLOG;
    }
}
