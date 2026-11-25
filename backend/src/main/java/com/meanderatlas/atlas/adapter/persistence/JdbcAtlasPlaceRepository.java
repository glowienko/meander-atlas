package com.meanderatlas.atlas.adapter.persistence;

import com.meanderatlas.atlas.domain.PlaceVisitState;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.atlas.domain.places.BoundingBox;
import com.meanderatlas.atlas.domain.places.MatchedPlace;
import com.meanderatlas.atlas.domain.places.PlaceQueryFilter;
import com.meanderatlas.atlas.domain.places.StoredMembership;
import com.meanderatlas.atlas.domain.places.StoredPlace;
import com.meanderatlas.atlas.domain.places.TimelineMatchCandidate;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    private static final double COASTAL_FALLBACK_METERS = 15_000.0;

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

    @Override
    public Optional<MatchedPlace> findCanonicalMatch(
            UserId userId,
            @Nullable String googlePlaceId,
            @Nullable Long googleCid,
            GeoPoint coordinates,
            String canonicalName,
            double radiusMeters,
            double minTrigramSimilarity) {
        if (googlePlaceId != null) {
            Optional<MatchedPlace> byPlaceId =
                    jdbcTemplate
                            .query(
                                    """
                                    SELECT id, category, category_source, google_place_id, google_cid,
                                           country_code, admin1_id
                                    FROM places
                                    WHERE user_id = CAST(:userId AS uuid)
                                      AND google_place_id = :googlePlaceId
                                    LIMIT 1
                                    """,
                                    Map.of(
                                            "userId",
                                            userId.value(),
                                            "googlePlaceId",
                                            googlePlaceId),
                                    JdbcAtlasPlaceRepository::mapMatchedPlace)
                            .stream()
                            .findFirst();
            if (byPlaceId.isPresent()) {
                return byPlaceId;
            }
        }
        if (googleCid != null) {
            Optional<MatchedPlace> byCid = findByGoogleCid(userId, googleCid);
            if (byCid.isPresent()) {
                return byCid;
            }
        }
        return jdbcTemplate
                .query(
                        """
                        SELECT id, category, category_source, google_place_id, google_cid,
                               country_code, admin1_id
                        FROM places
                        WHERE user_id = CAST(:userId AS uuid)
                          AND geom IS NOT NULL
                          AND ST_DWithin(
                              geom::geography,
                              ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                              :radiusMeters)
                          AND similarity(canonical_name, :canonicalName) >= :minSimilarity
                        ORDER BY ST_Distance(
                            geom::geography,
                            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) ASC
                        LIMIT 1
                        """,
                        new MapSqlParameterSource()
                                .addValue("userId", userId.value())
                                .addValue("lng", coordinates.longitude())
                                .addValue("lat", coordinates.latitude())
                                .addValue("radiusMeters", radiusMeters)
                                .addValue("canonicalName", canonicalName)
                                .addValue("minSimilarity", minTrigramSimilarity),
                        JdbcAtlasPlaceRepository::mapMatchedPlace)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<MatchedPlace> findByGoogleCid(UserId userId, long googleCid) {
        return jdbcTemplate
                .query(
                        """
                        SELECT id, category, category_source, google_place_id, google_cid,
                               country_code, admin1_id
                        FROM places
                        WHERE user_id = CAST(:userId AS uuid)
                          AND google_cid = :googleCid
                        LIMIT 1
                        """,
                        Map.of("userId", userId.value(), "googleCid", googleCid),
                        JdbcAtlasPlaceRepository::mapMatchedPlace)
                .stream()
                .findFirst();
    }

    @Override
    public PlaceId insertImportedPlace(
            UserId userId,
            String canonicalName,
            GeoPoint coordinates,
            CoordSource coordSource,
            @Nullable String googlePlaceId,
            @Nullable Long googleCid,
            PlaceCategory category,
            @Nullable String subcategory,
            CategorySource categorySource,
            DerivedRole derivedRole,
            @Nullable String fallbackCountryCode,
            @Nullable String cityName) {
        if (coordSource == CoordSource.GOOGLE_TEMP) {
            throw new IllegalArgumentException(
                    "GOOGLE_TEMP coordinates require explicit expiration TTL");
        }
        PlaceId id = new PlaceId(UUID.randomUUID());
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("id", id.value())
                        .addValue("userId", userId.value())
                        .addValue("canonicalName", canonicalName)
                        .addValue("lng", coordinates.longitude())
                        .addValue("lat", coordinates.latitude())
                        .addValue("coordSource", coordSource.name())
                        .addValue("googlePlaceId", googlePlaceId, Types.VARCHAR)
                        .addValue("googleCid", googleCid, Types.BIGINT)
                        .addValue("category", category.name())
                        .addValue("subcategory", subcategory, Types.VARCHAR)
                        .addValue("categorySource", categorySource.name())
                        .addValue("derivedRole", derivedRole.name())
                        .addValue("fallbackCountryCode", fallbackCountryCode, Types.VARCHAR)
                        .addValue("cityName", cityName, Types.VARCHAR)
                        .addValue("coastalFallbackMeters", COASTAL_FALLBACK_METERS);
        jdbcTemplate.update(
                """
                WITH pt AS (
                    SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geom
                ),
                resolved_country AS (
                    SELECT COALESCE(
                        (
                            SELECT rb.country_code
                            FROM region_boundaries rb, pt
                            WHERE rb.region_level = 'COUNTRY'
                              AND (
                                  ST_Covers(rb.geom, pt.geom)
                                  OR ST_DWithin(
                                      rb.geom::geography,
                                      pt.geom::geography,
                                      :coastalFallbackMeters)
                              )
                            ORDER BY ST_Covers(rb.geom, pt.geom) DESC,
                                     ST_Distance(rb.geom::geography, pt.geom::geography) ASC
                            LIMIT 1
                        ),
                        CAST(:fallbackCountryCode AS TEXT)
                    ) AS country_code
                ),
                resolved_admin1 AS (
                    SELECT (
                        SELECT rb.id
                        FROM region_boundaries rb, pt, resolved_country rc
                        WHERE rb.region_level = 'ADMIN1'
                          AND (
                              ST_Covers(rb.geom, pt.geom)
                              OR ST_DWithin(
                                  rb.geom::geography,
                                  pt.geom::geography,
                                  :coastalFallbackMeters)
                          )
                        ORDER BY ST_Covers(rb.geom, pt.geom) DESC,
                                 (rc.country_code IS NOT NULL
                                     AND rb.country_code = rc.country_code) DESC,
                                 ST_Distance(rb.geom::geography, pt.geom::geography) ASC
                        LIMIT 1
                    ) AS admin1_id
                )
                INSERT INTO places (
                    id,
                    user_id,
                    canonical_name,
                    geom,
                    coord_source,
                    google_place_id,
                    google_cid,
                    category,
                    subcategory,
                    category_source,
                    derived_role,
                    country_code,
                    admin1_id,
                    city_name
                )
                SELECT
                    CAST(:id AS uuid),
                    CAST(:userId AS uuid),
                    :canonicalName,
                    pt.geom,
                    :coordSource,
                    CAST(:googlePlaceId AS TEXT),
                    CAST(:googleCid AS BIGINT),
                    :category,
                    CAST(:subcategory AS TEXT),
                    :categorySource,
                    :derivedRole,
                    rc.country_code,
                    ra.admin1_id,
                    CAST(:cityName AS TEXT)
                FROM pt, resolved_country rc, resolved_admin1 ra
                """,
                params);
        return id;
    }

    @Override
    public void upsertMembership(
            UserId userId,
            PlaceId placeId,
            String accountLabel,
            String listName,
            ListKind listKind,
            @Nullable String note,
            @Nullable String tags,
            @Nullable String sourceUrl,
            @Nullable Instant savedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO place_memberships (
                    id, place_id, user_id, account_label, list_name, list_kind,
                    note, tags, source_url, saved_at
                )
                VALUES (
                    CAST(:id AS uuid),
                    CAST(:placeId AS uuid),
                    CAST(:userId AS uuid),
                    :accountLabel,
                    :listName,
                    :listKind,
                    CAST(:note AS TEXT),
                    CAST(:tags AS TEXT),
                    CAST(:sourceUrl AS TEXT),
                    CAST(:savedAt AS timestamptz)
                )
                ON CONFLICT (place_id, account_label, list_name) DO UPDATE
                SET list_kind = EXCLUDED.list_kind,
                    note = COALESCE(EXCLUDED.note, place_memberships.note),
                    tags = COALESCE(EXCLUDED.tags, place_memberships.tags),
                    source_url = COALESCE(EXCLUDED.source_url, place_memberships.source_url),
                    saved_at = COALESCE(EXCLUDED.saved_at, place_memberships.saved_at)
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("placeId", placeId.value())
                        .addValue("userId", userId.value())
                        .addValue("accountLabel", accountLabel)
                        .addValue("listName", listName)
                        .addValue("listKind", listKind.name())
                        .addValue("note", note, Types.VARCHAR)
                        .addValue("tags", tags, Types.VARCHAR)
                        .addValue("sourceUrl", sourceUrl, Types.VARCHAR)
                        .addValue(
                                "savedAt",
                                JdbcRowReaders.toOffsetDateTime(savedAt),
                                Types.TIMESTAMP_WITH_TIMEZONE));
    }

    @Override
    public List<ListKind> findMembershipListKinds(PlaceId placeId) {
        return jdbcTemplate.query(
                """
                SELECT DISTINCT list_kind
                FROM place_memberships
                WHERE place_id = CAST(:placeId AS uuid)
                """,
                Map.of("placeId", placeId.value()),
                (rs, _) -> ListKind.valueOf(JdbcRowReaders.requiredString(rs, "list_kind")));
    }

    @Override
    public void updatePlaceAfterMerge(
            UserId userId,
            PlaceId placeId,
            DerivedRole derivedRole,
            @Nullable PlaceCategory upgradedCategory,
            @Nullable String upgradedSubcategory,
            @Nullable CategorySource upgradedCategorySource,
            @Nullable String googlePlaceIdToBackfill,
            @Nullable Long googleCidToBackfill,
            @Nullable String fallbackCountryCode) {
        jdbcTemplate.update(
                """
                UPDATE places p
                SET derived_role = :derivedRole,
                    category = COALESCE(CAST(:upgradedCategory AS TEXT), p.category),
                    subcategory = CASE
                        WHEN CAST(:upgradedCategory AS TEXT) IS NOT NULL
                            THEN CAST(:upgradedSubcategory AS TEXT)
                        ELSE p.subcategory
                    END,
                    category_source = COALESCE(CAST(:upgradedCategorySource AS TEXT), p.category_source),
                    google_place_id = CASE
                        WHEN p.google_place_id IS NULL
                             AND CAST(:googlePlaceId AS TEXT) IS NOT NULL
                             AND NOT EXISTS (
                                 SELECT 1 FROM places o
                                 WHERE o.user_id = p.user_id
                                   AND o.google_place_id = CAST(:googlePlaceId AS TEXT)
                             )
                            THEN CAST(:googlePlaceId AS TEXT)
                        ELSE p.google_place_id
                    END,
                    google_cid = CASE
                        WHEN p.google_cid IS NULL
                             AND CAST(:googleCid AS BIGINT) IS NOT NULL
                             AND NOT EXISTS (
                                 SELECT 1 FROM places o
                                 WHERE o.user_id = p.user_id
                                   AND o.google_cid = CAST(:googleCid AS BIGINT)
                             )
                            THEN CAST(:googleCid AS BIGINT)
                        ELSE p.google_cid
                    END,
                    country_code = COALESCE(
                        p.country_code,
                        (
                            SELECT rb.country_code
                            FROM region_boundaries rb
                            WHERE rb.region_level = 'COUNTRY'
                              AND p.geom IS NOT NULL
                              AND (
                                  ST_Covers(rb.geom, p.geom)
                                  OR ST_DWithin(
                                      rb.geom::geography,
                                      p.geom::geography,
                                      :coastalFallbackMeters)
                              )
                            ORDER BY ST_Covers(rb.geom, p.geom) DESC,
                                     ST_Distance(rb.geom::geography, p.geom::geography) ASC
                            LIMIT 1
                        ),
                        CAST(:fallbackCountryCode AS TEXT)
                    ),
                    admin1_id = COALESCE(
                        p.admin1_id,
                        (
                            SELECT rb.id
                            FROM region_boundaries rb
                            WHERE rb.region_level = 'ADMIN1'
                              AND p.geom IS NOT NULL
                              AND (
                                  ST_Covers(rb.geom, p.geom)
                                  OR ST_DWithin(
                                      rb.geom::geography,
                                      p.geom::geography,
                                      :coastalFallbackMeters)
                              )
                            ORDER BY ST_Covers(rb.geom, p.geom) DESC,
                                     ST_Distance(rb.geom::geography, p.geom::geography) ASC
                            LIMIT 1
                        )
                    ),
                    updated_at = now()
                WHERE p.id = CAST(:placeId AS uuid)
                  AND p.user_id = CAST(:userId AS uuid)
                """,
                new MapSqlParameterSource()
                        .addValue("placeId", placeId.value())
                        .addValue("userId", userId.value())
                        .addValue("derivedRole", derivedRole.name())
                        .addValue(
                                "upgradedCategory",
                                upgradedCategory == null ? null : upgradedCategory.name(),
                                Types.VARCHAR)
                        .addValue("upgradedSubcategory", upgradedSubcategory, Types.VARCHAR)
                        .addValue(
                                "upgradedCategorySource",
                                upgradedCategorySource == null
                                        ? null
                                        : upgradedCategorySource.name(),
                                Types.VARCHAR)
                        .addValue("googlePlaceId", googlePlaceIdToBackfill, Types.VARCHAR)
                        .addValue("googleCid", googleCidToBackfill, Types.BIGINT)
                        .addValue("fallbackCountryCode", fallbackCountryCode, Types.VARCHAR)
                        .addValue("coastalFallbackMeters", COASTAL_FALLBACK_METERS));
    }

    @Override
    public Optional<TimelineMatchCandidate> findExactOrNearTimelineMatch(
            UserId userId,
            @Nullable String googlePlaceId,
            GeoPoint coordinates,
            double exactRadiusMeters) {
        if (googlePlaceId != null) {
            Optional<TimelineMatchCandidate> byPlaceId =
                    jdbcTemplate
                            .query(
                                    """
                                    SELECT p.id, p.google_place_id,
                                           COALESCE(pvs.status, 'UNVISITED') AS visit_status
                                    FROM places p
                                    LEFT JOIN place_visit_state pvs ON pvs.place_id = p.id
                                    WHERE p.user_id = CAST(:userId AS uuid)
                                      AND p.google_place_id = :googlePlaceId
                                    LIMIT 1
                                    """,
                                    Map.of(
                                            "userId",
                                            userId.value(),
                                            "googlePlaceId",
                                            googlePlaceId),
                                    JdbcAtlasPlaceRepository::mapTimelineCandidate)
                            .stream()
                            .findFirst();
            if (byPlaceId.isPresent()) {
                return byPlaceId;
            }
        }
        return jdbcTemplate
                .query(
                        """
                        SELECT p.id, p.google_place_id,
                               COALESCE(pvs.status, 'UNVISITED') AS visit_status
                        FROM places p
                        LEFT JOIN place_visit_state pvs ON pvs.place_id = p.id
                        WHERE p.user_id = CAST(:userId AS uuid)
                          AND p.geom IS NOT NULL
                          AND ST_DWithin(
                              p.geom::geography,
                              ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                              :radiusMeters)
                        ORDER BY ST_Distance(
                            p.geom::geography,
                            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) ASC
                        LIMIT 1
                        """,
                        Map.of(
                                "userId",
                                userId.value(),
                                "lng",
                                coordinates.longitude(),
                                "lat",
                                coordinates.latitude(),
                                "radiusMeters",
                                exactRadiusMeters),
                        JdbcAtlasPlaceRepository::mapTimelineCandidate)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<PlaceId> findUnvisitedTriageCandidate(
            UserId userId, GeoPoint coordinates, double minRadiusMeters, double maxRadiusMeters) {
        return jdbcTemplate
                .query(
                        """
                        SELECT p.id
                        FROM places p
                        LEFT JOIN place_visit_state pvs ON pvs.place_id = p.id
                        WHERE p.user_id = CAST(:userId AS uuid)
                          AND p.geom IS NOT NULL
                          AND COALESCE(pvs.status, 'UNVISITED') = 'UNVISITED'
                          AND ST_DWithin(
                              p.geom::geography,
                              ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                              :maxRadiusMeters)
                          AND ST_Distance(
                              p.geom::geography,
                              ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) >= :minRadiusMeters
                        ORDER BY ST_Distance(
                            p.geom::geography,
                            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) ASC
                        LIMIT 1
                        """,
                        Map.of(
                                "userId",
                                userId.value(),
                                "lng",
                                coordinates.longitude(),
                                "lat",
                                coordinates.latitude(),
                                "minRadiusMeters",
                                minRadiusMeters,
                                "maxRadiusMeters",
                                maxRadiusMeters),
                        (rs, _) -> new PlaceId(JdbcRowReaders.requiredUuid(rs, "id")))
                .stream()
                .findFirst();
    }

    @Override
    public void backfillGooglePlaceId(UserId userId, PlaceId placeId, String googlePlaceId) {
        jdbcTemplate.update(
                """
                UPDATE places p
                SET google_place_id = :googlePlaceId,
                    updated_at = now()
                WHERE p.id = CAST(:placeId AS uuid)
                  AND p.user_id = CAST(:userId AS uuid)
                  AND p.google_place_id IS NULL
                  AND NOT EXISTS (
                      SELECT 1 FROM places o
                      WHERE o.user_id = p.user_id
                        AND o.google_place_id = :googlePlaceId
                  )
                """,
                Map.of(
                        "placeId",
                        placeId.value(),
                        "userId",
                        userId.value(),
                        "googlePlaceId",
                        googlePlaceId));
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

    private static MatchedPlace mapMatchedPlace(ResultSet rs, int rowNum) throws SQLException {
        return new MatchedPlace(
                new PlaceId(JdbcRowReaders.requiredUuid(rs, "id")),
                PlaceCategory.valueOf(JdbcRowReaders.requiredString(rs, "category")),
                CategorySource.valueOf(JdbcRowReaders.requiredString(rs, "category_source")),
                rs.getString("google_place_id"),
                rs.getObject("google_cid", Long.class),
                rs.getString("country_code"),
                rs.getObject("admin1_id", UUID.class));
    }

    private static TimelineMatchCandidate mapTimelineCandidate(ResultSet rs, int rowNum)
            throws SQLException {
        return new TimelineMatchCandidate(
                new PlaceId(JdbcRowReaders.requiredUuid(rs, "id")),
                rs.getString("google_place_id"),
                VisitStatus.valueOf(JdbcRowReaders.requiredString(rs, "visit_status")));
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
