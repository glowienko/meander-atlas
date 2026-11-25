package com.meanderatlas.atlas;

import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.PlaceCategory;
import org.jspecify.annotations.Nullable;

public record RegionFilterQuery(
        @Nullable String q,
        @Nullable DerivedRole derivedRole,
        @Nullable PlaceCategory category,
        @Nullable String account,
        @Nullable String list) {}
