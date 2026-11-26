package com.meanderatlas.atlas.adapter.persistence;

import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.PlaceCategory;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

final class AtlasPlaceFilterSql {

    private AtlasPlaceFilterSql() {}

    static void appendCommonPredicates(
            Stream.Builder<String> predicates,
            MapSqlParameterSource params,
            @Nullable String q,
            @Nullable DerivedRole derivedRole,
            @Nullable PlaceCategory category,
            @Nullable String account,
            @Nullable String list) {
        if (q != null) {
            params.addValue("qPattern", toSubstringLikePattern(q));
            predicates.add(
                    """
                    (p.canonical_name ILIKE :qPattern ESCAPE '\\'
                        OR p.city_name ILIKE :qPattern ESCAPE '\\'
                        OR EXISTS (
                            SELECT 1 FROM place_memberships pmq
                            WHERE pmq.place_id = p.id
                              AND pmq.note ILIKE :qPattern ESCAPE '\\'
                        ))
                    """
                            .stripTrailing());
        }
        if (derivedRole != null) {
            params.addValue("derivedRole", derivedRole.name());
            predicates.add("p.derived_role = :derivedRole");
        }
        if (category != null) {
            params.addValue("category", category.name());
            predicates.add("p.category = :category");
        }
        if (account != null || list != null) {
            Stream.Builder<String> membershipPredicates = Stream.builder();
            membershipPredicates.add("pmf.place_id = p.id");
            if (account != null) {
                params.addValue("account", account);
                membershipPredicates.add("pmf.account_label = :account");
            }
            if (list != null) {
                params.addValue("list", list);
                membershipPredicates.add("pmf.list_name = :list");
            }
            predicates.add(
                    membershipPredicates
                            .build()
                            .collect(
                                    Collectors.joining(
                                            "\n      AND ",
                                            "EXISTS (\n    SELECT 1 FROM place_memberships pmf\n    WHERE ",
                                            "\n)")));
        }
    }

    private static String toSubstringLikePattern(String raw) {
        String escaped = raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
