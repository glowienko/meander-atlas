package com.meanderatlas.atlas.adapter.persistence;

import com.meanderatlas.atlas.domain.RegionLevel;
import com.meanderatlas.atlas.domain.places.PlaceQueryFilter;
import com.meanderatlas.atlas.domain.stats.AtlasStats;
import com.meanderatlas.atlas.domain.stats.AtlasStatsRepository;
import com.meanderatlas.atlas.domain.stats.RegionSummary;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
            PlaceQueryFilter filter) {
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("userId", userId.value())
                        .addValue("regionLevel", level.name())
                        .addValue("countryCode", countryCode, Types.VARCHAR)
                        .addValue("q", filter.q(), Types.VARCHAR)
                        .addValue(
                                "derivedRole",
                                filter.derivedRole() == null ? null : filter.derivedRole().name(),
                                Types.VARCHAR)
                        .addValue(
                                "category",
                                filter.category() == null ? null : filter.category().name(),
                                Types.VARCHAR)
                        .addValue("account", filter.account(), Types.VARCHAR)
                        .addValue("list", filter.list(), Types.VARCHAR);
        return jdbcTemplate.query(
                """
                WITH filtered_places AS (
                    SELECT
                        p.id,
                        p.country_code,
                        p.admin1_id,
                        COALESCE(pvs.status, 'UNVISITED') AS visit_status
                    FROM places p
                    LEFT JOIN place_visit_state pvs ON pvs.place_id = p.id
                    WHERE p.user_id = CAST(:userId AS uuid)
                      AND p.geom IS NOT NULL
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
                )
                SELECT
                    rb.id,
                    rb.region_level,
                    rb.code,
                    rb.country_code,
                    rb.name,
                    COUNT(fp.id)::int AS total_places,
                    COUNT(fp.id) FILTER (WHERE fp.visit_status = 'VISITED')::int AS visited_places,
                    COUNT(fp.id) FILTER (WHERE fp.visit_status = 'UNVISITED')::int AS unvisited_places,
                    COUNT(fp.id) FILTER (WHERE fp.visit_status = 'NEEDS_TRIAGE')::int AS needs_triage_places,
                    ST_XMin(rb.simplified_geom) AS min_lng,
                    ST_YMin(rb.simplified_geom) AS min_lat,
                    ST_XMax(rb.simplified_geom) AS max_lng,
                    ST_YMax(rb.simplified_geom) AS max_lat
                FROM region_boundaries rb
                JOIN filtered_places fp
                  ON (rb.region_level = 'COUNTRY' AND fp.country_code = rb.code)
                  OR (rb.region_level = 'ADMIN1' AND fp.admin1_id = rb.id)
                WHERE rb.region_level = :regionLevel
                  AND (CAST(:countryCode AS TEXT) IS NULL OR rb.country_code = CAST(:countryCode AS TEXT))
                GROUP BY rb.id
                ORDER BY visited_places DESC, total_places DESC, rb.name ASC
                """,
                params,
                JdbcAtlasStatsRepository::mapRegionSummary);
    }

    @Override
    public Optional<RegionSummary> findRegionSummary(
            UserId userId, RegionLevel level, String code) {
        return jdbcTemplate
                .query(
                        """
                        SELECT
                            rb.id,
                            rb.region_level,
                            rb.code,
                            rb.country_code,
                            rb.name,
                            COUNT(p.id)::int AS total_places,
                            COUNT(p.id) FILTER (
                                WHERE COALESCE(pvs.status, 'UNVISITED') = 'VISITED'
                            )::int AS visited_places,
                            COUNT(p.id) FILTER (
                                WHERE COALESCE(pvs.status, 'UNVISITED') = 'UNVISITED'
                            )::int AS unvisited_places,
                            COUNT(p.id) FILTER (
                                WHERE COALESCE(pvs.status, 'UNVISITED') = 'NEEDS_TRIAGE'
                            )::int AS needs_triage_places,
                            ST_XMin(rb.simplified_geom) AS min_lng,
                            ST_YMin(rb.simplified_geom) AS min_lat,
                            ST_XMax(rb.simplified_geom) AS max_lng,
                            ST_YMax(rb.simplified_geom) AS max_lat
                        FROM region_boundaries rb
                        LEFT JOIN places p
                          ON p.user_id = CAST(:userId AS uuid)
                         AND p.geom IS NOT NULL
                         AND ((rb.region_level = 'COUNTRY' AND p.country_code = rb.code)
                             OR (rb.region_level = 'ADMIN1' AND p.admin1_id = rb.id))
                        LEFT JOIN place_visit_state pvs ON pvs.place_id = p.id
                        WHERE rb.region_level = :regionLevel
                          AND rb.code = :code
                        GROUP BY rb.id
                        """,
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
                                        WHERE COALESCE(pvs.status, 'UNVISITED') = 'VISITED'
                                    )::int AS visited_places,
                                    COUNT(p.id) FILTER (
                                        WHERE COALESCE(pvs.status, 'UNVISITED') = 'UNVISITED'
                                    )::int AS unvisited_places,
                                    COUNT(p.id) FILTER (
                                        WHERE COALESCE(pvs.status, 'UNVISITED') = 'NEEDS_TRIAGE'
                                    )::int AS needs_triage_places,
                                    COUNT(DISTINCT p.country_code) FILTER (
                                        WHERE COALESCE(pvs.status, 'UNVISITED') = 'VISITED'
                                          AND p.country_code IS NOT NULL
                                    )::int AS visited_countries,
                                    COUNT(DISTINCT p.country_code) FILTER (
                                        WHERE p.country_code IS NOT NULL
                                    )::int AS total_saved_countries,
                                    COUNT(DISTINCT p.admin1_id) FILTER (
                                        WHERE COALESCE(pvs.status, 'UNVISITED') = 'VISITED'
                                          AND p.admin1_id IS NOT NULL
                                    )::int AS visited_admin1,
                                    COUNT(DISTINCT p.admin1_id) FILTER (
                                        WHERE p.admin1_id IS NOT NULL
                                    )::int AS total_saved_admin1
                                FROM places p
                                LEFT JOIN place_visit_state pvs ON pvs.place_id = p.id
                                WHERE p.user_id = CAST(:userId AS uuid)
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
                                WHERE COALESCE(pvs.status, 'UNVISITED') = 'VISITED'
                            )::int AS visited_count
                        FROM places p
                        LEFT JOIN place_visit_state pvs ON pvs.place_id = p.id
                        WHERE p.user_id = CAST(:userId AS uuid)
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
                                WHERE COALESCE(pvs.status, 'UNVISITED') = 'VISITED'
                            )::int AS visited_count
                        FROM places p
                        LEFT JOIN place_visit_state pvs ON pvs.place_id = p.id
                        WHERE p.user_id = CAST(:userId AS uuid)
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
                            COUNT(DISTINCT p.id)::int AS place_count
                        FROM place_memberships pm
                        JOIN places p ON p.id = pm.place_id
                        WHERE pm.user_id = CAST(:userId AS uuid)
                          AND p.user_id = CAST(:userId AS uuid)
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
