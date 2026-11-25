package com.meanderatlas.atlas.adapter.persistence;

import com.meanderatlas.atlas.domain.PlaceVisitState;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.atlas.domain.places.BoundingBox;
import com.meanderatlas.atlas.domain.places.PlaceQueryFilter;
import com.meanderatlas.atlas.domain.places.StoredMembership;
import com.meanderatlas.atlas.domain.places.StoredPlace;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAtlasPlaceRepository implements AtlasPlaceRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcAtlasPlaceRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<StoredPlace> findPlaces(UserId userId, PlaceQueryFilter filter) {
        BoundingBox bbox = filter.bbox();
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("userId", userId.value())
                        .addValue("q", filter.q(), Types.VARCHAR)
                        .addValue(
                                "visitStatus",
                                filter.visitStatus() == null ? null : filter.visitStatus().name(),
                                Types.VARCHAR)
                        .addValue(
                                "derivedRole",
                                filter.derivedRole() == null ? null : filter.derivedRole().name(),
                                Types.VARCHAR)
                        .addValue(
                                "category",
                                filter.category() == null ? null : filter.category().name(),
                                Types.VARCHAR)
                        .addValue("account", filter.account(), Types.VARCHAR)
                        .addValue("list", filter.list(), Types.VARCHAR)
                        .addValue("countryCode", filter.countryCode(), Types.VARCHAR)
                        .addValue("admin1Id", filter.admin1Id(), Types.OTHER)
                        .addValue("hasBbox", bbox != null)
                        .addValue("minLng", bbox == null ? 0.0 : bbox.minLng())
                        .addValue("minLat", bbox == null ? 0.0 : bbox.minLat())
                        .addValue("maxLng", bbox == null ? 0.0 : bbox.maxLng())
                        .addValue("maxLat", bbox == null ? 0.0 : bbox.maxLat());

        List<PlaceRow> rows =
                jdbcTemplate.query(
                        """
                        SELECT
                            p.id,
                            p.canonical_name,
                            ST_Y(p.geom) AS lat,
                            ST_X(p.geom) AS lng,
                            p.category,
                            p.subcategory,
                            p.category_source,
                            p.derived_role,
                            COALESCE(pvs.status, 'UNVISITED') AS visit_status,
                            pvs.source AS visit_source,
                            COALESCE(pvs.visit_count, 0) AS visit_count,
                            pvs.first_visited_at,
                            pvs.last_visited_at,
                            p.country_code,
                            p.admin1_id,
                            rb.code AS admin1_code,
                            rb.name AS admin1_name,
                            p.city_name
                        FROM places p
                        LEFT JOIN place_visit_state pvs ON pvs.place_id = p.id
                        LEFT JOIN region_boundaries rb ON rb.id = p.admin1_id
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
                          AND (CAST(:visitStatus AS TEXT) IS NULL
                              OR COALESCE(pvs.status, 'UNVISITED') = CAST(:visitStatus AS TEXT))
                          AND (CAST(:derivedRole AS TEXT) IS NULL
                              OR p.derived_role = CAST(:derivedRole AS TEXT))
                          AND (CAST(:category AS TEXT) IS NULL
                              OR p.category = CAST(:category AS TEXT))
                          AND (CAST(:countryCode AS TEXT) IS NULL
                              OR p.country_code = CAST(:countryCode AS TEXT))
                          AND (CAST(:admin1Id AS uuid) IS NULL
                              OR p.admin1_id = CAST(:admin1Id AS uuid))
                          AND ((CAST(:account AS TEXT) IS NULL AND CAST(:list AS TEXT) IS NULL)
                              OR EXISTS (
                                  SELECT 1 FROM place_memberships pmf
                                  WHERE pmf.place_id = p.id
                                    AND (CAST(:account AS TEXT) IS NULL
                                        OR pmf.account_label = CAST(:account AS TEXT))
                                    AND (CAST(:list AS TEXT) IS NULL
                                        OR pmf.list_name = CAST(:list AS TEXT))
                              ))
                          AND (:hasBbox = false
                              OR p.geom && ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326))
                        ORDER BY p.canonical_name ASC, p.id ASC
                        """,
                        params,
                        (rs, _) -> {
                            String rawVisitSource = rs.getString("visit_source");
                            return new PlaceRow(
                                    new PlaceId(JdbcRowReaders.requiredUuid(rs, "id")),
                                    JdbcRowReaders.requiredString(rs, "canonical_name"),
                                    new GeoPoint(rs.getDouble("lat"), rs.getDouble("lng")),
                                    PlaceCategory.valueOf(
                                            JdbcRowReaders.requiredString(rs, "category")),
                                    rs.getString("subcategory"),
                                    CategorySource.valueOf(
                                            JdbcRowReaders.requiredString(rs, "category_source")),
                                    DerivedRole.valueOf(
                                            JdbcRowReaders.requiredString(rs, "derived_role")),
                                    VisitStatus.valueOf(
                                            JdbcRowReaders.requiredString(rs, "visit_status")),
                                    rawVisitSource == null
                                            ? null
                                            : VisitSource.valueOf(rawVisitSource),
                                    rs.getInt("visit_count"),
                                    JdbcRowReaders.nullableInstant(rs, "first_visited_at"),
                                    JdbcRowReaders.nullableInstant(rs, "last_visited_at"),
                                    rs.getString("country_code"),
                                    rs.getObject("admin1_id", UUID.class),
                                    rs.getString("admin1_code"),
                                    rs.getString("admin1_name"),
                                    rs.getString("city_name"));
                        });
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<PlaceId, List<StoredMembership>> membershipsByPlace =
                loadMemberships(rows.stream().map(row -> row.id().value()).toList());
        return rows.stream()
                .map(row ->
                        new StoredPlace(
                                row.id(),
                                row.canonicalName(),
                                row.coordinates(),
                                row.category(),
                                row.subcategory(),
                                row.categorySource(),
                                row.derivedRole(),
                                row.visitStatus(),
                                row.visitSource(),
                                row.visitCount(),
                                row.firstVisitedAt(),
                                row.lastVisitedAt(),
                                row.countryCode(),
                                row.admin1Id(),
                                row.admin1Code(),
                                row.admin1Name(),
                                row.cityName(),
                                membershipsByPlace.getOrDefault(row.id(), List.of())))
                .toList();
    }

    @Override
    public Optional<PlaceVisitState> findVisitState(UserId userId, PlaceId placeId) {
        return findVisitStates(userId, List.of(placeId)).stream().findFirst();
    }

    @Override
    public List<PlaceVisitState> findVisitStates(UserId userId, List<PlaceId> placeIds) {
        if (placeIds.isEmpty()) {
            return List.of();
        }
        List<UUID> rawIds = placeIds.stream().map(PlaceId::value).toList();
        return jdbcTemplate.query(
                """
                SELECT place_id, user_id, status, source, visit_count, first_visited_at, last_visited_at
                FROM place_visit_state
                WHERE user_id = CAST(:userId AS uuid)
                  AND place_id IN (:placeIds)
                """,
                Map.of("userId", userId.value(), "placeIds", rawIds),
                (rs, _) -> {
                    String rawSource = rs.getString("source");
                    return new PlaceVisitState(
                            new PlaceId(JdbcRowReaders.requiredUuid(rs, "place_id")),
                            new UserId(JdbcRowReaders.requiredUuid(rs, "user_id")),
                            VisitStatus.valueOf(JdbcRowReaders.requiredString(rs, "status")),
                            rawSource == null ? null : VisitSource.valueOf(rawSource),
                            rs.getInt("visit_count"),
                            JdbcRowReaders.nullableInstant(rs, "first_visited_at"),
                            JdbcRowReaders.nullableInstant(rs, "last_visited_at"));
                });
    }

    @Override
    public boolean existsPlaceForUser(UserId userId, PlaceId placeId) {
        Boolean exists =
                jdbcTemplate.queryForObject(
                        """
                        SELECT EXISTS(
                            SELECT 1 FROM places
                            WHERE id = CAST(:placeId AS uuid)
                              AND user_id = CAST(:userId AS uuid)
                        )
                        """,
                        Map.of("placeId", placeId.value(), "userId", userId.value()),
                        Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Set<PlaceId> findOwnedPlaceIds(UserId userId, List<PlaceId> placeIds) {
        if (placeIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> rawIds = placeIds.stream().map(PlaceId::value).toList();
        return jdbcTemplate
                .query(
                        """
                        SELECT id FROM places
                        WHERE user_id = CAST(:userId AS uuid)
                          AND id IN (:placeIds)
                        """,
                        Map.of("userId", userId.value(), "placeIds", rawIds),
                        (rs, _) -> new PlaceId(JdbcRowReaders.requiredUuid(rs, "id")))
                .stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void upsertVisitStates(List<PlaceVisitState> states) {
        if (states.isEmpty()) {
            return;
        }
        SqlParameterSource[] params =
                states.stream()
                        .map(state ->
                                new MapSqlParameterSource()
                                        .addValue("placeId", state.placeId().value())
                                        .addValue("userId", state.userId().value())
                                        .addValue("status", state.status().name())
                                        .addValue(
                                                "source",
                                                state.source() == null
                                                        ? null
                                                        : state.source().name(),
                                                Types.VARCHAR)
                                        .addValue("visitCount", state.visitCount())
                                        .addValue(
                                                "firstVisitedAt",
                                                JdbcRowReaders.toOffsetDateTime(
                                                        state.firstVisitedAt()),
                                                Types.TIMESTAMP_WITH_TIMEZONE)
                                        .addValue(
                                                "lastVisitedAt",
                                                JdbcRowReaders.toOffsetDateTime(
                                                        state.lastVisitedAt()),
                                                Types.TIMESTAMP_WITH_TIMEZONE))
                        .toArray(SqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO place_visit_state
                    (place_id, user_id, status, source, visit_count, first_visited_at, last_visited_at, updated_at)
                VALUES
                    (CAST(:placeId AS uuid), CAST(:userId AS uuid), :status, CAST(:source AS text),
                     :visitCount, CAST(:firstVisitedAt AS timestamptz), CAST(:lastVisitedAt AS timestamptz), now())
                ON CONFLICT (place_id) DO UPDATE
                SET status = EXCLUDED.status,
                    source = EXCLUDED.source,
                    visit_count = EXCLUDED.visit_count,
                    first_visited_at = EXCLUDED.first_visited_at,
                    last_visited_at = EXCLUDED.last_visited_at,
                    updated_at = now()
                """,
                params);
    }

    private Map<PlaceId, List<StoredMembership>> loadMemberships(List<UUID> placeIds) {
        List<StoredMembership> memberships =
                jdbcTemplate.query(
                        """
                        SELECT
                            place_id,
                            account_label,
                            list_name,
                            list_kind,
                            note,
                            tags,
                            source_url,
                            saved_at
                        FROM place_memberships
                        WHERE place_id IN (:placeIds)
                        ORDER BY saved_at DESC NULLS LAST, account_label ASC, list_name ASC
                        """,
                        Map.of("placeIds", placeIds),
                        (rs, _) ->
                                new StoredMembership(
                                        new PlaceId(JdbcRowReaders.requiredUuid(rs, "place_id")),
                                        JdbcRowReaders.requiredString(rs, "account_label"),
                                        JdbcRowReaders.requiredString(rs, "list_name"),
                                        ListKind.valueOf(
                                                JdbcRowReaders.requiredString(rs, "list_kind")),
                                        rs.getString("note"),
                                        rs.getString("tags"),
                                        rs.getString("source_url"),
                                        JdbcRowReaders.nullableInstant(rs, "saved_at")));
        return memberships.stream()
                .collect(
                        Collectors.groupingBy(
                                StoredMembership::placeId, Collectors.toUnmodifiableList()));
    }

    private record PlaceRow(
            PlaceId id,
            String canonicalName,
            GeoPoint coordinates,
            PlaceCategory category,
            @Nullable String subcategory,
            CategorySource categorySource,
            DerivedRole derivedRole,
            VisitStatus visitStatus,
            @Nullable VisitSource visitSource,
            int visitCount,
            @Nullable Instant firstVisitedAt,
            @Nullable Instant lastVisitedAt,
            @Nullable String countryCode,
            @Nullable UUID admin1Id,
            @Nullable String admin1Code,
            @Nullable String admin1Name,
            @Nullable String cityName) {}
}
