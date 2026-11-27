package com.meanderatlas.atlas.adapter.persistence;

import com.meanderatlas.atlas.domain.PlaceVisitState;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.atlas.domain.places.BoundingBox;
import com.meanderatlas.atlas.domain.places.MatchedPlace;
import com.meanderatlas.atlas.domain.places.PlaceEnrichmentTarget;
import com.meanderatlas.atlas.domain.places.PlaceQueryFilter;
import com.meanderatlas.atlas.domain.places.StoredMembership;
import com.meanderatlas.atlas.domain.places.StoredPlace;
import com.meanderatlas.atlas.domain.places.TimelineMatchCandidate;
import com.meanderatlas.atlas.domain.places.TimelineMatchCandidate.MatchConfidence;
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
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAtlasPlaceRepository implements AtlasPlaceRepository {

    private static final String INSERT_IMPORTED_PLACE_SQL =
            """
            WITH pt AS (
                SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geom
            ),
            resolved_country AS (
                SELECT
                    pt.geom,
                    COALESCE(%s, CAST(:fallbackCountryCode AS TEXT)) AS country_code
                FROM pt
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
                rc.geom,
                :coordSource,
                CAST(:googlePlaceId AS TEXT),
                CAST(:googleCid AS BIGINT),
                :category,
                CAST(:subcategory AS TEXT),
                :categorySource,
                :derivedRole,
                rc.country_code,
                %s,
                CAST(:cityName AS TEXT)
            FROM resolved_country rc
            """
                    .formatted(
                            SpatialRegionSql.countryLookupSql("pt.geom"),
                            SpatialRegionSql.admin1LookupSql("rc.geom", "rc.country_code"));

    private static final String UPDATE_PLACE_AFTER_MERGE_SQL =
            """
            WITH target_geom AS (
                SELECT
                    p.id,
                    p.country_code AS existing_country_code,
                    p.admin1_id AS existing_admin1_id,
                    CASE
                        WHEN CAST(:upgradedCoordSource AS TEXT) IS NOT NULL
                            THEN ST_SetSRID(ST_MakePoint(:upgradedLng, :upgradedLat), 4326)
                        ELSE p.geom
                    END AS next_geom
                FROM places p
                WHERE p.id = CAST(:placeId AS uuid)
                  AND p.user_id = CAST(:userId AS uuid)
            ),
            resolved_country AS (
                SELECT
                    tg.id,
                    tg.next_geom,
                    tg.existing_admin1_id,
                    CASE
                        WHEN CAST(:upgradedCoordSource AS TEXT) IS NOT NULL
                            THEN COALESCE(
                                %s,
                                CAST(:fallbackCountryCode AS TEXT),
                                tg.existing_country_code
                            )
                        ELSE COALESCE(
                            tg.existing_country_code,
                            %s,
                            CAST(:fallbackCountryCode AS TEXT)
                        )
                    END AS next_country_code
                FROM target_geom tg
            )
            UPDATE places p
            SET geom = rc.next_geom,
                coord_source = COALESCE(CAST(:upgradedCoordSource AS TEXT), p.coord_source),
                derived_role = :derivedRole,
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
                country_code = rc.next_country_code,
                admin1_id = CASE
                    WHEN CAST(:upgradedCoordSource AS TEXT) IS NOT NULL
                        THEN COALESCE(%s, rc.existing_admin1_id)
                    ELSE COALESCE(rc.existing_admin1_id, %s)
                END,
                updated_at = now()
            FROM resolved_country rc
            WHERE p.id = rc.id
            """
                    .formatted(
                            SpatialRegionSql.countryLookupSql("tg.next_geom"),
                            SpatialRegionSql.countryLookupSql("tg.next_geom"),
                            SpatialRegionSql.admin1LookupSql(
                                    "rc.next_geom", "rc.next_country_code"),
                            SpatialRegionSql.admin1LookupSql(
                                    "rc.next_geom", "rc.next_country_code"));

    private static final String APPLY_PLACE_ENRICHMENT_SQL =
            """
            WITH target_geom AS (
                SELECT
                    p.id,
                    p.country_code AS existing_country_code,
                    p.admin1_id AS existing_admin1_id,
                    (:upgradeCoordinates
                     AND (p.coord_source = 'TAKEOUT_S2_APPROX' OR p.coord_source IS NULL))
                        AS can_upgrade_coords,
                    CASE
                        WHEN :upgradeCoordinates
                             AND (p.coord_source = 'TAKEOUT_S2_APPROX' OR p.coord_source IS NULL)
                            THEN ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)
                        ELSE p.geom
                    END AS next_geom
                FROM places p
                WHERE p.id = CAST(:placeId AS uuid)
                  AND p.user_id = CAST(:userId AS uuid)
            ),
            resolved_country AS (
                SELECT
                    tg.id,
                    tg.can_upgrade_coords,
                    tg.next_geom,
                    tg.existing_admin1_id,
                    CASE
                        WHEN tg.can_upgrade_coords
                            THEN COALESCE(%s, tg.existing_country_code)
                        ELSE tg.existing_country_code
                    END AS next_country_code
                FROM target_geom tg
            )
            UPDATE places p
            SET canonical_name = COALESCE(CAST(:upgradedCanonicalName AS TEXT), p.canonical_name),
                geom = rc.next_geom,
                coord_source = CASE
                    WHEN rc.can_upgrade_coords THEN 'OPEN_CATALOG'
                    ELSE p.coord_source
                END,
                osm_id = COALESCE(CAST(:osmId AS TEXT), p.osm_id),
                category = CASE
                    WHEN :upgradeCategory AND p.category_source <> 'MANUAL'
                        THEN CAST(:category AS TEXT)
                    ELSE p.category
                END,
                subcategory = CASE
                    WHEN :upgradeCategory AND p.category_source <> 'MANUAL'
                        THEN CAST(:subcategory AS TEXT)
                    ELSE p.subcategory
                END,
                category_source = CASE
                    WHEN :upgradeCategory AND p.category_source <> 'MANUAL'
                        THEN 'OPEN_CATALOG'
                    ELSE p.category_source
                END,
                derived_role = :derivedRole,
                country_code = rc.next_country_code,
                admin1_id = CASE
                    WHEN rc.can_upgrade_coords
                        THEN COALESCE(%s, rc.existing_admin1_id)
                    ELSE rc.existing_admin1_id
                END,
                city_name = COALESCE(NULLIF(p.city_name, ''), CAST(:cityName AS TEXT)),
                photo_url = COALESCE(NULLIF(p.photo_url, ''), CAST(:photoUrl AS TEXT)),
                opening_hours_json = COALESCE(
                    CAST(:openingHoursJson AS jsonb), p.opening_hours_json),
                updated_at = now()
            FROM resolved_country rc
            WHERE p.id = rc.id
            """
                    .formatted(
                            SpatialRegionSql.countryLookupSql("tg.next_geom"),
                            SpatialRegionSql.admin1LookupSql(
                                    "rc.next_geom", "rc.next_country_code"));

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcAtlasPlaceRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<StoredPlace> findPlaces(UserId userId, PlaceQueryFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId.value());
        Stream.Builder<String> predicates = Stream.builder();
        predicates.add("p.user_id = :userId");
        predicates.add("p.geom IS NOT NULL");
        AtlasPlaceFilterSql.appendCommonPredicates(
                predicates,
                params,
                filter.q(),
                filter.derivedRole(),
                filter.category(),
                filter.account(),
                filter.list());
        appendPlaceSpecificPredicates(predicates, params, filter);

        String sql =
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
                    p.city_name,
                    p.coord_source,
                    p.photo_url
                FROM places p
                LEFT JOIN place_visit_state pvs ON pvs.place_id = p.id
                LEFT JOIN region_boundaries rb ON rb.id = p.admin1_id
                WHERE %s
                ORDER BY p.canonical_name ASC, p.id ASC
                """
                        .formatted(predicates.build().collect(Collectors.joining("\n  AND ")));
        List<StoredPlace> rows =
                jdbcTemplate.query(sql, params, JdbcAtlasPlaceRepository::mapStoredPlace);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<PlaceId, List<StoredMembership>> membershipsByPlace =
                loadMemberships(rows.stream().map(place -> place.id().value()).toList());
        return rows.stream()
                .map(place ->
                        place.withMemberships(
                                membershipsByPlace.getOrDefault(place.id(), List.of())))
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
                WHERE user_id = :userId
                  AND place_id = ANY(:placeIds)
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId.value())
                        .addValue("placeIds", JdbcRowReaders.uuidArray(rawIds)),
                (rs, _) -> {
                    PlaceId placeId = new PlaceId(JdbcRowReaders.requiredUuid(rs, "place_id"));
                    UserId rowUserId = new UserId(JdbcRowReaders.requiredUuid(rs, "user_id"));
                    return mapVisitStateColumns(rs, placeId, rowUserId);
                });
    }

    @Override
    public boolean existsPlaceForUser(UserId userId, PlaceId placeId) {
        Boolean exists =
                jdbcTemplate.queryForObject(
                        """
                        SELECT EXISTS(
                            SELECT 1 FROM places
                            WHERE id = :placeId
                              AND user_id = :userId
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
                        WHERE user_id = :userId
                          AND id = ANY(:placeIds)
                        """,
                        new MapSqlParameterSource()
                                .addValue("userId", userId.value())
                                .addValue("placeIds", JdbcRowReaders.uuidArray(rawIds)),
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
                WHERE place_visit_state.user_id = EXCLUDED.user_id
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
        return Optional.ofNullable(googlePlaceId)
                .flatMap(id -> findByGooglePlaceId(userId, id))
                .or(() ->
                        Optional.ofNullable(googleCid).flatMap(cid -> findByGoogleCid(userId, cid)))
                .or(() ->
                        findBySpatialSimilarity(
                                userId,
                                coordinates,
                                canonicalName,
                                radiusMeters,
                                minTrigramSimilarity));
    }

    @Override
    public Optional<MatchedPlace> findByGoogleCid(UserId userId, long googleCid) {
        return jdbcTemplate
                .query(
                        """
                        SELECT id, category, category_source, coord_source, google_place_id, google_cid,
                               country_code, admin1_id
                        FROM places
                        WHERE user_id = :userId
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
        PlaceId id = PlaceId.random();
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
                        .addValue(
                                "coastalFallbackMeters", SpatialRegionSql.COASTAL_FALLBACK_METERS);
        jdbcTemplate.update(INSERT_IMPORTED_PLACE_SQL, params);
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
                WHERE place_memberships.user_id = EXCLUDED.user_id
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
                WHERE place_id = :placeId
                """,
                Map.of("placeId", placeId.value()),
                (rs, _) -> ListKind.valueOf(JdbcRowReaders.requiredString(rs, "list_kind")));
    }

    @Override
    public void updatePlaceAfterMerge(
            UserId userId,
            PlaceId placeId,
            DerivedRole derivedRole,
            @Nullable GeoPoint upgradedCoordinates,
            @Nullable CoordSource upgradedCoordSource,
            @Nullable PlaceCategory upgradedCategory,
            @Nullable String upgradedSubcategory,
            @Nullable CategorySource upgradedCategorySource,
            @Nullable String googlePlaceIdToBackfill,
            @Nullable Long googleCidToBackfill,
            @Nullable String fallbackCountryCode) {
        jdbcTemplate.update(
                UPDATE_PLACE_AFTER_MERGE_SQL,
                new MapSqlParameterSource()
                        .addValue("placeId", placeId.value())
                        .addValue("userId", userId.value())
                        .addValue("derivedRole", derivedRole.name())
                        .addValue(
                                "upgradedLng",
                                upgradedCoordinates == null ? 0.0 : upgradedCoordinates.longitude())
                        .addValue(
                                "upgradedLat",
                                upgradedCoordinates == null ? 0.0 : upgradedCoordinates.latitude())
                        .addValue(
                                "upgradedCoordSource",
                                upgradedCoordSource == null ? null : upgradedCoordSource.name(),
                                Types.VARCHAR)
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
                        .addValue(
                                "coastalFallbackMeters", SpatialRegionSql.COASTAL_FALLBACK_METERS));
    }

    @Override
    public List<PlaceId> findPlaceIdsNeedingEnrichment(UserId userId) {
        return jdbcTemplate.query(
                """
                SELECT id
                FROM places
                WHERE user_id = :userId
                  AND geom IS NOT NULL
                  AND (
                      coord_source = 'TAKEOUT_S2_APPROX'
                      OR (
                          coord_source = 'TAKEOUT'
                          AND (category_source = 'HEURISTIC' OR photo_url IS NULL OR osm_id IS NULL)
                      )
                  )
                ORDER BY created_at ASC, id ASC
                """,
                Map.of("userId", userId.value()),
                (rs, _) -> new PlaceId(JdbcRowReaders.requiredUuid(rs, "id")));
    }

    @Override
    public Optional<TimelineMatchCandidate> findTimelineMatchCandidate(
            UserId userId,
            @Nullable String googlePlaceId,
            GeoPoint coordinates,
            double exactRadiusMeters,
            double triageMaxRadiusMeters) {
        return Optional.ofNullable(googlePlaceId)
                .flatMap(id -> findTimelineCandidateByGooglePlaceId(userId, id))
                .or(() ->
                        findTimelineCandidateByDistance(
                                userId, coordinates, exactRadiusMeters, triageMaxRadiusMeters));
    }

    @Override
    public void backfillGooglePlaceId(UserId userId, PlaceId placeId, String googlePlaceId) {
        jdbcTemplate.update(
                """
                UPDATE places p
                SET google_place_id = :googlePlaceId,
                    updated_at = now()
                WHERE p.id = :placeId
                  AND p.user_id = :userId
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

    @Override
    public List<PlaceEnrichmentTarget> findEnrichmentTargets(
            UserId userId, List<PlaceId> placeIds) {
        if (placeIds.isEmpty()) {
            return List.of();
        }
        List<UUID> rawIds = placeIds.stream().map(PlaceId::value).toList();
        return jdbcTemplate.query(
                """
                SELECT id, canonical_name, ST_Y(geom) AS lat, ST_X(geom) AS lng,
                       coord_source, category, category_source, osm_id, photo_url,
                       country_code, city_name
                FROM places
                WHERE user_id = :userId
                  AND geom IS NOT NULL
                  AND id = ANY(:placeIds)
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId.value())
                        .addValue("placeIds", JdbcRowReaders.uuidArray(rawIds)),
                (rs, _) -> {
                    String rawCoordSource = rs.getString("coord_source");
                    CoordSource coordSource =
                            rawCoordSource == null
                                    ? CoordSource.TAKEOUT_S2_APPROX
                                    : CoordSource.valueOf(rawCoordSource);
                    return new PlaceEnrichmentTarget(
                            new PlaceId(JdbcRowReaders.requiredUuid(rs, "id")),
                            JdbcRowReaders.requiredString(rs, "canonical_name"),
                            new GeoPoint(rs.getDouble("lat"), rs.getDouble("lng")),
                            coordSource,
                            PlaceCategory.valueOf(JdbcRowReaders.requiredString(rs, "category")),
                            CategorySource.valueOf(
                                    JdbcRowReaders.requiredString(rs, "category_source")),
                            rs.getString("osm_id"),
                            rs.getString("photo_url"),
                            rs.getString("country_code"),
                            rs.getString("city_name"));
                });
    }

    @Override
    public void applyPlaceEnrichment(
            UserId userId,
            PlaceId placeId,
            @Nullable String upgradedCanonicalName,
            boolean upgradeCoordinates,
            @Nullable GeoPoint refinedCoordinates,
            @Nullable String osmId,
            boolean upgradeCategory,
            @Nullable PlaceCategory category,
            @Nullable String subcategory,
            DerivedRole derivedRole,
            @Nullable String cityName,
            @Nullable String photoUrl,
            @Nullable String openingHoursJson) {
        jdbcTemplate.update(
                APPLY_PLACE_ENRICHMENT_SQL,
                new MapSqlParameterSource()
                        .addValue("placeId", placeId.value())
                        .addValue("userId", userId.value())
                        .addValue("upgradedCanonicalName", upgradedCanonicalName, Types.VARCHAR)
                        .addValue("upgradeCoordinates", upgradeCoordinates)
                        .addValue(
                                "lng",
                                refinedCoordinates == null ? 0.0 : refinedCoordinates.longitude())
                        .addValue(
                                "lat",
                                refinedCoordinates == null ? 0.0 : refinedCoordinates.latitude())
                        .addValue("osmId", osmId, Types.VARCHAR)
                        .addValue("upgradeCategory", upgradeCategory)
                        .addValue(
                                "category",
                                category == null ? null : category.name(),
                                Types.VARCHAR)
                        .addValue("subcategory", subcategory, Types.VARCHAR)
                        .addValue("derivedRole", derivedRole.name())
                        .addValue("cityName", cityName, Types.VARCHAR)
                        .addValue("photoUrl", photoUrl, Types.VARCHAR)
                        .addValue("openingHoursJson", openingHoursJson, Types.VARCHAR)
                        .addValue(
                                "coastalFallbackMeters", SpatialRegionSql.COASTAL_FALLBACK_METERS));
    }

    private Optional<MatchedPlace> findByGooglePlaceId(UserId userId, String googlePlaceId) {
        return jdbcTemplate
                .query(
                        """
                        SELECT id, category, category_source, coord_source, google_place_id, google_cid,
                               country_code, admin1_id
                        FROM places
                        WHERE user_id = :userId
                          AND google_place_id = :googlePlaceId
                        LIMIT 1
                        """,
                        Map.of("userId", userId.value(), "googlePlaceId", googlePlaceId),
                        JdbcAtlasPlaceRepository::mapMatchedPlace)
                .stream()
                .findFirst();
    }

    private Optional<MatchedPlace> findBySpatialSimilarity(
            UserId userId,
            GeoPoint coordinates,
            String canonicalName,
            double radiusMeters,
            double minTrigramSimilarity) {
        return jdbcTemplate
                .query(
                        """
                        WITH target AS (
                            SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS geog
                        )
                        SELECT p.id, p.category, p.category_source, p.coord_source,
                               p.google_place_id, p.google_cid, p.country_code, p.admin1_id
                        FROM places p
                        CROSS JOIN target
                        WHERE p.user_id = :userId
                          AND p.geom IS NOT NULL
                          AND ST_DWithin(p.geom::geography, target.geog, :radiusMeters)
                          AND similarity(p.canonical_name, :canonicalName) >= :minSimilarity
                        ORDER BY ST_Distance(p.geom::geography, target.geog) ASC,
                                 similarity(p.canonical_name, :canonicalName) DESC,
                                 p.id ASC
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

    private Optional<TimelineMatchCandidate> findTimelineCandidateByGooglePlaceId(
            UserId userId, String googlePlaceId) {
        return jdbcTemplate
                .query(
                        """
                        SELECT p.id, p.google_place_id,
                               pvs.status, pvs.source, pvs.visit_count,
                               pvs.first_visited_at, pvs.last_visited_at
                        FROM places p
                        LEFT JOIN place_visit_state pvs ON pvs.place_id = p.id
                        WHERE p.user_id = :userId
                          AND p.google_place_id = :googlePlaceId
                        LIMIT 1
                        """,
                        Map.of("userId", userId.value(), "googlePlaceId", googlePlaceId),
                        (rs, _) -> mapTimelineCandidate(rs, userId, MatchConfidence.EXACT))
                .stream()
                .findFirst();
    }

    private Optional<TimelineMatchCandidate> findTimelineCandidateByDistance(
            UserId userId,
            GeoPoint coordinates,
            double exactRadiusMeters,
            double triageMaxRadiusMeters) {
        return jdbcTemplate
                .query(
                        """
                        WITH target AS (
                            SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS geog
                        ),
                        candidates AS (
                            SELECT
                                p.id,
                                p.google_place_id,
                                pvs.status,
                                pvs.source,
                                pvs.visit_count,
                                pvs.first_visited_at,
                                pvs.last_visited_at,
                                ST_Distance(p.geom::geography, target.geog) AS distance_meters
                            FROM places p
                            CROSS JOIN target
                            LEFT JOIN place_visit_state pvs ON pvs.place_id = p.id
                            WHERE p.user_id = :userId
                              AND p.geom IS NOT NULL
                              AND ST_DWithin(p.geom::geography, target.geog, :triageMaxRadiusMeters)
                        )
                        SELECT id, google_place_id, status, source, visit_count,
                               first_visited_at, last_visited_at, distance_meters
                        FROM candidates
                        WHERE distance_meters <= :exactRadiusMeters
                           OR COALESCE(status, 'UNVISITED') = 'UNVISITED'
                        ORDER BY (distance_meters <= :exactRadiusMeters) DESC,
                                 distance_meters ASC,
                                 id ASC
                        LIMIT 1
                        """,
                        Map.of(
                                "userId",
                                userId.value(),
                                "lng",
                                coordinates.longitude(),
                                "lat",
                                coordinates.latitude(),
                                "exactRadiusMeters",
                                exactRadiusMeters,
                                "triageMaxRadiusMeters",
                                triageMaxRadiusMeters),
                        (rs, _) -> {
                            double distanceMeters = rs.getDouble("distance_meters");
                            MatchConfidence confidence =
                                    distanceMeters <= exactRadiusMeters
                                            ? MatchConfidence.EXACT
                                            : MatchConfidence.TRIAGE;
                            return mapTimelineCandidate(rs, userId, confidence);
                        })
                .stream()
                .findFirst();
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
                        WHERE place_id = ANY(:placeIds)
                        ORDER BY saved_at DESC NULLS LAST, account_label ASC, list_name ASC
                        """,
                        new MapSqlParameterSource("placeIds", JdbcRowReaders.uuidArray(placeIds)),
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

    private static void appendPlaceSpecificPredicates(
            Stream.Builder<String> predicates,
            MapSqlParameterSource params,
            PlaceQueryFilter filter) {
        VisitStatus visitStatus = filter.visitStatus();
        if (visitStatus == VisitStatus.UNVISITED) {
            predicates.add("(pvs.status IS NULL OR pvs.status = 'UNVISITED')");
        } else if (visitStatus != null) {
            params.addValue("visitStatus", visitStatus.name());
            predicates.add("pvs.status = :visitStatus");
        }
        if (filter.countryCode() != null) {
            params.addValue("countryCode", filter.countryCode());
            predicates.add("p.country_code = :countryCode");
        }
        if (filter.admin1Id() != null) {
            params.addValue("admin1Id", filter.admin1Id());
            predicates.add("p.admin1_id = :admin1Id");
        }
        BoundingBox bbox = filter.bbox();
        if (bbox != null) {
            params.addValue("minLng", bbox.minLng())
                    .addValue("minLat", bbox.minLat())
                    .addValue("maxLng", bbox.maxLng())
                    .addValue("maxLat", bbox.maxLat());
            predicates.add(
                    bbox.crossesAntimeridian()
                            ? "(p.geom && ST_MakeEnvelope(:minLng, :minLat, 180.0, :maxLat, 4326)"
                                    + " OR p.geom && ST_MakeEnvelope(-180.0, :minLat, :maxLng, :maxLat, 4326))"
                            : "p.geom && ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326)");
        }
    }

    private static StoredPlace mapStoredPlace(ResultSet rs, int rowNum) throws SQLException {
        String rawVisitSource = rs.getString("visit_source");
        return new StoredPlace(
                new PlaceId(JdbcRowReaders.requiredUuid(rs, "id")),
                JdbcRowReaders.requiredString(rs, "canonical_name"),
                new GeoPoint(rs.getDouble("lat"), rs.getDouble("lng")),
                PlaceCategory.valueOf(JdbcRowReaders.requiredString(rs, "category")),
                rs.getString("subcategory"),
                CategorySource.valueOf(JdbcRowReaders.requiredString(rs, "category_source")),
                DerivedRole.valueOf(JdbcRowReaders.requiredString(rs, "derived_role")),
                VisitStatus.valueOf(JdbcRowReaders.requiredString(rs, "visit_status")),
                rawVisitSource == null ? null : VisitSource.valueOf(rawVisitSource),
                rs.getInt("visit_count"),
                JdbcRowReaders.nullableInstant(rs, "first_visited_at"),
                JdbcRowReaders.nullableInstant(rs, "last_visited_at"),
                rs.getString("country_code"),
                rs.getObject("admin1_id", UUID.class),
                rs.getString("admin1_code"),
                rs.getString("admin1_name"),
                rs.getString("city_name"),
                CoordSource.valueOf(JdbcRowReaders.requiredString(rs, "coord_source")),
                rs.getString("photo_url"),
                List.of());
    }

    private static MatchedPlace mapMatchedPlace(ResultSet rs, int rowNum) throws SQLException {
        return new MatchedPlace(
                new PlaceId(JdbcRowReaders.requiredUuid(rs, "id")),
                PlaceCategory.valueOf(JdbcRowReaders.requiredString(rs, "category")),
                CategorySource.valueOf(JdbcRowReaders.requiredString(rs, "category_source")),
                CoordSource.valueOf(JdbcRowReaders.requiredString(rs, "coord_source")),
                rs.getString("google_place_id"),
                rs.getObject("google_cid", Long.class),
                rs.getString("country_code"),
                rs.getObject("admin1_id", UUID.class));
    }

    private static TimelineMatchCandidate mapTimelineCandidate(
            ResultSet rs, UserId userId, MatchConfidence confidence) throws SQLException {
        PlaceId placeId = new PlaceId(JdbcRowReaders.requiredUuid(rs, "id"));
        PlaceVisitState visitState = mapVisitStateColumns(rs, placeId, userId);
        return new TimelineMatchCandidate(
                placeId, rs.getString("google_place_id"), visitState, confidence);
    }

    private static PlaceVisitState mapVisitStateColumns(
            ResultSet rs, PlaceId placeId, UserId userId) throws SQLException {
        String rawStatus = rs.getString("status");
        if (rawStatus == null) {
            return PlaceVisitState.unvisited(placeId, userId);
        }
        String rawSource = rs.getString("source");
        return new PlaceVisitState(
                placeId,
                userId,
                VisitStatus.valueOf(rawStatus),
                rawSource == null ? null : VisitSource.valueOf(rawSource),
                rs.getInt("visit_count"),
                JdbcRowReaders.nullableInstant(rs, "first_visited_at"),
                JdbcRowReaders.nullableInstant(rs, "last_visited_at"));
    }
}
