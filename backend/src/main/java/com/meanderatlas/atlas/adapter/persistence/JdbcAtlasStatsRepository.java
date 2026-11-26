package com.meanderatlas.atlas.adapter.persistence;

import com.meanderatlas.atlas.domain.RegionLevel;
import com.meanderatlas.atlas.domain.stats.AtlasStats;
import com.meanderatlas.atlas.domain.stats.AtlasStatsRepository;
import com.meanderatlas.atlas.domain.stats.RegionSummary;
import com.meanderatlas.atlas.domain.stats.RegionSummaryFilter;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAtlasStatsRepository implements AtlasStatsRepository {

    private static final int TOP_SUBCATEGORIES_LIMIT = 15;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcAtlasStatsRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<RegionSummary> findRegionSummaries(
            UserId userId,
            RegionLevel level,
            @Nullable String countryCode,
            RegionSummaryFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId.value());
        Stream.Builder<String> placePredicates = Stream.builder();
        placePredicates.add("p.user_id = :userId");
        placePredicates.add("p.geom IS NOT NULL");
        AtlasPlaceFilterSql.appendCommonPredicates(
                placePredicates,
                params,
                filter.q(),
                filter.derivedRole(),
                filter.category(),
                filter.account(),
                filter.list());

        RegionGrouping grouping =
                switch (level) {
                    case COUNTRY -> {
                        placePredicates.add("p.country_code IS NOT NULL");
                        if (countryCode != null) {
                            params.addValue("countryCode", countryCode);
                            placePredicates.add("p.country_code = :countryCode");
                        }
                        yield new RegionGrouping(
                                "p.country_code",
                                "rb.region_level = 'COUNTRY' AND rb.code = rc.region_key",
                                "");
                    }
                    case ADMIN1 -> {
                        placePredicates.add("p.admin1_id IS NOT NULL");
                        String outerWhere = "";
                        if (countryCode != null) {
                            params.addValue("countryCode", countryCode);
                            outerWhere = "WHERE rb.country_code = :countryCode";
                        }
                        yield new RegionGrouping(
                                "p.admin1_id",
                                "rb.id = rc.region_key AND rb.region_level = 'ADMIN1'",
                                outerWhere);
                    }
                    case CITY ->
                            throw new IllegalArgumentException(
                                    "CITY level is not supported for region summaries");
                };

        String sql =
                """
                WITH region_counts AS (
                    SELECT
                        %s AS region_key,
                        COUNT(p.id)::int AS total_places,
                        COUNT(p.id) FILTER (WHERE pvs.status = 'VISITED')::int AS visited_places,
                        COUNT(p.id) FILTER (
                            WHERE pvs.status IS NULL OR pvs.status = 'UNVISITED'
                        )::int AS unvisited_places,
                        COUNT(p.id) FILTER (WHERE pvs.status = 'NEEDS_TRIAGE')::int AS needs_triage_places
                    FROM places p
                    LEFT JOIN place_visit_state pvs ON pvs.place_id = p.id
                    WHERE %s
                    GROUP BY %s
                )
                SELECT
                    rb.id,
                    rb.region_level,
                    rb.code,
                    rb.country_code,
                    rb.name,
                    rc.total_places,
                    rc.visited_places,
                    rc.unvisited_places,
                    rc.needs_triage_places,
                    ST_XMin(rb.simplified_geom) AS min_lng,
                    ST_YMin(rb.simplified_geom) AS min_lat,
                    ST_XMax(rb.simplified_geom) AS max_lng,
                    ST_YMax(rb.simplified_geom) AS max_lat
                FROM region_counts rc
                JOIN region_boundaries rb
                  ON %s
                %s
                ORDER BY rc.visited_places DESC, rc.total_places DESC, rb.name ASC
                """
                        .formatted(
                                grouping.keyColumn(),
                                placePredicates.build().collect(Collectors.joining("\n  AND ")),
                                grouping.keyColumn(),
                                grouping.joinClause(),
                                grouping.outerWhereClause());
        return jdbcTemplate.query(sql, params, JdbcAtlasStatsRepository::mapRegionSummary);
    }

    @Override
    public Optional<RegionSummary> findRegionSummary(
            UserId userId, RegionLevel level, String code) {
        String joinPredicate =
                switch (level) {
                    case COUNTRY -> "p.country_code = rb.code";
                    case ADMIN1 -> "p.admin1_id = rb.id";
                    case CITY ->
                            throw new IllegalArgumentException(
                                    "CITY level is not supported for region summary");
                };
        String sql =
                """
                SELECT
                    rb.id,
                    rb.region_level,
                    rb.code,
                    rb.country_code,
                    rb.name,
                    COUNT(p.id)::int AS total_places,
                    COUNT(p.id) FILTER (WHERE pvs.status = 'VISITED')::int AS visited_places,
                    COUNT(p.id) FILTER (
                        WHERE pvs.status IS NULL OR pvs.status = 'UNVISITED'
                    )::int AS unvisited_places,
                    COUNT(p.id) FILTER (
                        WHERE pvs.status = 'NEEDS_TRIAGE'
                    )::int AS needs_triage_places,
                    ST_XMin(rb.simplified_geom) AS min_lng,
                    ST_YMin(rb.simplified_geom) AS min_lat,
                    ST_XMax(rb.simplified_geom) AS max_lng,
                    ST_YMax(rb.simplified_geom) AS max_lat
                FROM region_boundaries rb
                LEFT JOIN places p
                  ON p.user_id = :userId
                 AND p.geom IS NOT NULL
                 AND %s
                LEFT JOIN place_visit_state pvs ON pvs.place_id = p.id
                WHERE rb.region_level = :regionLevel
                  AND rb.code = :code
                GROUP BY rb.id
                """
                        .formatted(joinPredicate);
        return jdbcTemplate
                .query(
                        sql,
                        Map.of(
                                "userId", userId.value(),
                                "regionLevel", level.name(),
                                "code", code),
                        JdbcAtlasStatsRepository::mapRegionSummary)
                .stream()
                .findFirst();
    }

    @Override
    public AtlasStats fetchStats(UserId userId) {
        TotalsRow totals =
                jdbcTemplate
                        .query(
                                """
                                SELECT
                                    COUNT(p.id)::int AS total_mapped,
                                    COUNT(p.id) FILTER (
                                        WHERE pvs.status = 'VISITED'
                                    )::int AS visited_places,
                                    COUNT(p.id) FILTER (
                                        WHERE pvs.status IS NULL OR pvs.status = 'UNVISITED'
                                    )::int AS unvisited_places,
                                    COUNT(p.id) FILTER (
                                        WHERE pvs.status = 'NEEDS_TRIAGE'
                                    )::int AS needs_triage_places,
                                    COUNT(DISTINCT p.country_code) FILTER (
                                        WHERE pvs.status = 'VISITED'
                                    )::int AS visited_countries,
                                    COUNT(DISTINCT p.country_code)::int AS total_saved_countries,
                                    COUNT(DISTINCT p.admin1_id) FILTER (
                                        WHERE pvs.status = 'VISITED'
                                    )::int AS visited_admin1,
                                    COUNT(DISTINCT p.admin1_id)::int AS total_saved_admin1
                                FROM places p
                                LEFT JOIN place_visit_state pvs ON pvs.place_id = p.id
                                WHERE p.user_id = :userId
                                  AND p.geom IS NOT NULL
                                """,
                                Map.of("userId", userId.value()),
                                (rs, _) ->
                                        new TotalsRow(
                                                rs.getInt("total_mapped"),
                                                rs.getInt("visited_places"),
                                                rs.getInt("unvisited_places"),
                                                rs.getInt("needs_triage_places"),
                                                rs.getInt("visited_countries"),
                                                rs.getInt("total_saved_countries"),
                                                rs.getInt("visited_admin1"),
                                                rs.getInt("total_saved_admin1")))
                        .stream()
                        .findFirst()
                        .orElse(new TotalsRow(0, 0, 0, 0, 0, 0, 0, 0));

        List<AtlasStats.CategoryCount> categories =
                jdbcTemplate.query(
                        """
                        SELECT
                            p.category,
                            COUNT(p.id)::int AS total_count,
                            COUNT(p.id) FILTER (
                                WHERE pvs.status = 'VISITED'
                            )::int AS visited_count
                        FROM places p
                        LEFT JOIN place_visit_state pvs ON pvs.place_id = p.id
                        WHERE p.user_id = :userId
                          AND p.geom IS NOT NULL
                        GROUP BY p.category
                        ORDER BY total_count DESC, p.category ASC
                        """,
                        Map.of("userId", userId.value()),
                        (rs, _) ->
                                new AtlasStats.CategoryCount(
                                        PlaceCategory.valueOf(
                                                JdbcRowReaders.requiredString(rs, "category")),
                                        rs.getInt("total_count"),
                                        rs.getInt("visited_count")));

        List<AtlasStats.SubcategoryCount> topSubcategories =
                jdbcTemplate.query(
                        """
                        SELECT
                            p.subcategory,
                            p.category,
                            COUNT(p.id)::int AS total_count,
                            COUNT(p.id) FILTER (
                                WHERE pvs.status = 'VISITED'
                            )::int AS visited_count
                        FROM places p
                        LEFT JOIN place_visit_state pvs ON pvs.place_id = p.id
                        WHERE p.user_id = :userId
                          AND p.geom IS NOT NULL
                          AND p.subcategory IS NOT NULL
                        GROUP BY p.subcategory, p.category
                        ORDER BY total_count DESC, p.subcategory ASC
                        LIMIT :limit
                        """,
                        Map.of("userId", userId.value(), "limit", TOP_SUBCATEGORIES_LIMIT),
                        (rs, _) ->
                                new AtlasStats.SubcategoryCount(
                                        JdbcRowReaders.requiredString(rs, "subcategory"),
                                        PlaceCategory.valueOf(
                                                JdbcRowReaders.requiredString(rs, "category")),
                                        rs.getInt("total_count"),
                                        rs.getInt("visited_count")));

        List<AtlasStats.AccountListCount> accountLists =
                jdbcTemplate.query(
                        """
                        SELECT
                            pm.account_label,
                            pm.list_name,
                            pm.list_kind,
                            COUNT(*)::int AS place_count
                        FROM place_memberships pm
                        JOIN places p ON p.id = pm.place_id
                        WHERE pm.user_id = :userId
                          AND p.user_id = :userId
                          AND p.geom IS NOT NULL
                        GROUP BY pm.account_label, pm.list_name, pm.list_kind
                        ORDER BY place_count DESC, pm.account_label ASC, pm.list_name ASC
                        """,
                        Map.of("userId", userId.value()),
                        (rs, _) ->
                                new AtlasStats.AccountListCount(
                                        JdbcRowReaders.requiredString(rs, "account_label"),
                                        JdbcRowReaders.requiredString(rs, "list_name"),
                                        ListKind.valueOf(
                                                JdbcRowReaders.requiredString(rs, "list_kind")),
                                        rs.getInt("place_count")));

        return new AtlasStats(
                totals.totalMapped(),
                totals.visitedPlaces(),
                totals.unvisitedPlaces(),
                totals.needsTriagePlaces(),
                totals.visitedCountries(),
                totals.totalSavedCountries(),
                totals.visitedAdmin1(),
                totals.totalSavedAdmin1(),
                categories,
                topSubcategories,
                accountLists);
    }

    private static RegionSummary mapRegionSummary(ResultSet rs, int rowNum) throws SQLException {
        return new RegionSummary(
                JdbcRowReaders.requiredUuid(rs, "id"),
                RegionLevel.valueOf(JdbcRowReaders.requiredString(rs, "region_level")),
                JdbcRowReaders.requiredString(rs, "code"),
                JdbcRowReaders.requiredString(rs, "country_code"),
                JdbcRowReaders.requiredString(rs, "name"),
                rs.getInt("total_places"),
                rs.getInt("visited_places"),
                rs.getInt("unvisited_places"),
                rs.getInt("needs_triage_places"),
                rs.getDouble("min_lng"),
                rs.getDouble("min_lat"),
                rs.getDouble("max_lng"),
                rs.getDouble("max_lat"));
    }

    private record RegionGrouping(String keyColumn, String joinClause, String outerWhereClause) {}

    private record TotalsRow(
            int totalMapped,
            int visitedPlaces,
            int unvisitedPlaces,
            int needsTriagePlaces,
            int visitedCountries,
            int totalSavedCountries,
            int visitedAdmin1,
            int totalSavedAdmin1) {}
}
