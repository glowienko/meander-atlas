package com.meanderatlas.atlas.adapter.persistence;

import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.PlaceCategory;
import java.sql.Types;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

final class AtlasPlaceFilterSql {

    static final String COMMON_PLACE_PREDICATES =
            """
            AND (CAST(:q AS TEXT) IS NULL
                OR strpos(lower(p.canonical_name), lower(CAST(:q AS TEXT))) > 0
                OR strpos(lower(COALESCE(p.city_name, '')), lower(CAST(:q AS TEXT))) > 0
                OR EXISTS (
                    SELECT 1 FROM place_memberships pmq
                    WHERE pmq.place_id = p.id
                      AND strpos(lower(COALESCE(pmq.note, '')), lower(CAST(:q AS TEXT))) > 0
                ))
            AND (CAST(:derivedRole AS TEXT) IS NULL
                OR p.derived_role = CAST(:derivedRole AS TEXT))
            AND (CAST(:category AS TEXT) IS NULL
                OR p.category = CAST(:category AS TEXT))
            AND ((CAST(:account AS TEXT) IS NULL AND CAST(:list AS TEXT) IS NULL)
                OR EXISTS (
                    SELECT 1 FROM place_memberships pmf
                    WHERE pmf.place_id = p.id
                      AND (CAST(:account AS TEXT) IS NULL
                          OR pmf.account_label = CAST(:account AS TEXT))
                      AND (CAST(:list AS TEXT) IS NULL
                          OR pmf.list_name = CAST(:list AS TEXT))
                ))
            """;

    private AtlasPlaceFilterSql() {}

    static MapSqlParameterSource addCommonFilterParams(
            MapSqlParameterSource params,
            @Nullable String q,
            @Nullable DerivedRole derivedRole,
            @Nullable PlaceCategory category,
            @Nullable String account,
            @Nullable String list) {
        return params.addValue("q", q, Types.VARCHAR)
                .addValue(
                        "derivedRole",
                        derivedRole == null ? null : derivedRole.name(),
                        Types.VARCHAR)
                .addValue("category", category == null ? null : category.name(), Types.VARCHAR)
                .addValue("account", account, Types.VARCHAR)
                .addValue("list", list, Types.VARCHAR);
    }
}
