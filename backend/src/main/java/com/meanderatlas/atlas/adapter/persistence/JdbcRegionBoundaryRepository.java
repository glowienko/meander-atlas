package com.meanderatlas.atlas.adapter.persistence;

import com.meanderatlas.atlas.domain.RegionLevel;
import com.meanderatlas.atlas.domain.boundaries.BoundaryFeature;
import com.meanderatlas.atlas.domain.boundaries.RegionBoundaryRepository;
import java.sql.Statement;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRegionBoundaryRepository implements RegionBoundaryRepository {

    private static final int UPSERT_BATCH_SIZE = 250;
    private static final double COASTAL_FALLBACK_METERS = 15_000.0;
    private static final double COASTAL_BBOX_EXPAND_DEGREES = 0.6;

    private static final String UPSERT_SQL =
            """
            WITH parsed AS (
                SELECT
                    CAST(:id AS uuid) AS id,
                    :regionLevel AS region_level,
                    :code AS code,
                    :countryCode AS country_code,
                    :name AS name,
                    ST_Multi(
                        ST_CollectionExtract(
                            ST_MakeValid(ST_SetSRID(ST_GeomFromGeoJSON(:geometryJson), 4326)),
                            3
                        )
                    ) AS valid_geom
            ),
            simplified AS (
                SELECT
                    id,
                    region_level,
                    code,
                    country_code,
                    name,
                    valid_geom,
                    ST_Multi(
                        ST_CollectionExtract(
                            ST_SimplifyPreserveTopology(valid_geom, :tolerance),
                            3
                        )
                    ) AS candidate_simplified
                FROM parsed
                WHERE valid_geom IS NOT NULL AND NOT ST_IsEmpty(valid_geom)
            )
            INSERT INTO region_boundaries (id, region_level, code, country_code, name, geom, simplified_geom)
            SELECT
                id,
                region_level,
                code,
                country_code,
                name,
                valid_geom,
                CASE
                    WHEN candidate_simplified IS NULL OR ST_IsEmpty(candidate_simplified)
                        THEN valid_geom
                    ELSE candidate_simplified
                END
            FROM simplified
            ON CONFLICT (region_level, code) DO UPDATE
            SET country_code = EXCLUDED.country_code,
                name = EXCLUDED.name,
                geom = EXCLUDED.geom,
                simplified_geom = EXCLUDED.simplified_geom
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcRegionBoundaryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int upsertBoundaries(List<BoundaryFeature> features, double simplifyTolerance) {
        if (features.isEmpty()) {
            return 0;
        }
        return IntStream.iterate(0, i -> i < features.size(), i -> i + UPSERT_BATCH_SIZE)
                .mapToObj(start ->
                        features.subList(
                                start, Math.min(start + UPSERT_BATCH_SIZE, features.size())))
                .mapToInt(batch -> executeUpsertBatch(batch, simplifyTolerance))
                .sum();
    }

    @Override
    public long countByLevel(RegionLevel level) {
        Long count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM region_boundaries
                        WHERE region_level = :regionLevel
                        """,
                        Map.of("regionLevel", level.name()),
                        Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public int assignRegionsToUntaggedPlaces() {
        return jdbcTemplate.update(
                """
                WITH resolved AS (
                    SELECT
                        p.id,
                        COALESCE(
                            p.country_code,
                            (
                                SELECT rb.country_code
                                FROM region_boundaries rb
                                WHERE rb.region_level = 'COUNTRY'
                                  AND rb.geom && p.geom
                                  AND ST_Covers(rb.geom, p.geom)
                                LIMIT 1
                            ),
                            (
                                SELECT rb.country_code
                                FROM region_boundaries rb
                                WHERE rb.region_level = 'COUNTRY'
                                  AND rb.geom && ST_Expand(p.geom, :coastalExpandDegrees)
                                  AND ST_DWithin(
                                      rb.geom::geography,
                                      p.geom::geography,
                                      :coastalFallbackMeters)
                                ORDER BY ST_Distance(rb.geom::geography, p.geom::geography) ASC
                                LIMIT 1
                            )
                        ) AS next_country_code,
                        COALESCE(
                            p.admin1_id,
                            (
                                SELECT rb.id
                                FROM region_boundaries rb
                                WHERE rb.region_level = 'ADMIN1'
                                  AND rb.geom && p.geom
                                  AND ST_Covers(rb.geom, p.geom)
                                ORDER BY (p.country_code IS NOT NULL
                                             AND rb.country_code = p.country_code) DESC
                                LIMIT 1
                            ),
                            (
                                SELECT rb.id
                                FROM region_boundaries rb
                                WHERE rb.region_level = 'ADMIN1'
                                  AND rb.geom && ST_Expand(p.geom, :coastalExpandDegrees)
                                  AND ST_DWithin(
                                      rb.geom::geography,
                                      p.geom::geography,
                                      :coastalFallbackMeters)
                                ORDER BY (p.country_code IS NOT NULL
                                             AND rb.country_code = p.country_code) DESC,
                                         ST_Distance(rb.geom::geography, p.geom::geography) ASC
                                LIMIT 1
                            )
                        ) AS next_admin1_id
                    FROM places p
                    WHERE p.geom IS NOT NULL
                      AND (p.country_code IS NULL OR p.admin1_id IS NULL)
                )
                UPDATE places p
                SET country_code = COALESCE(resolved.next_country_code, p.country_code),
                    admin1_id = COALESCE(resolved.next_admin1_id, p.admin1_id),
                    updated_at = now()
                FROM resolved
                WHERE p.id = resolved.id
                  AND (
                      p.country_code IS DISTINCT FROM resolved.next_country_code
                      OR p.admin1_id IS DISTINCT FROM resolved.next_admin1_id
                  )
                """,
                Map.of(
                        "coastalFallbackMeters",
                        COASTAL_FALLBACK_METERS,
                        "coastalExpandDegrees",
                        COASTAL_BBOX_EXPAND_DEGREES));
    }

    @Override
    public byte[] fetchRegionMvtTile(
            RegionLevel level, int z, int x, int y, @Nullable String countryCode) {
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("regionLevel", level.name())
                        .addValue("z", z)
                        .addValue("x", x)
                        .addValue("y", y)
                        .addValue("countryCode", countryCode, Types.VARCHAR);
        byte[] tile =
                jdbcTemplate.queryForObject(
                        """
                        WITH bounds AS (
                            SELECT ST_TileEnvelope(:z, :x, :y) AS tile_geom
                        ),
                        mercator_safe AS (
                            SELECT
                                rb.id,
                                rb.code,
                                rb.name,
                                rb.region_level,
                                rb.country_code,
                                ST_CollectionExtract(
                                    ST_Intersection(
                                        rb.simplified_geom,
                                        ST_MakeEnvelope(-180.0, -85.05112878, 180.0, 85.05112878, 4326)
                                    ),
                                    3
                                ) AS clipped_geom,
                                bounds.tile_geom
                            FROM region_boundaries rb
                            CROSS JOIN bounds
                            WHERE rb.region_level = :regionLevel
                              AND (CAST(:countryCode AS TEXT) IS NULL
                                  OR rb.country_code = CAST(:countryCode AS TEXT))
                              AND rb.simplified_geom && ST_Transform(bounds.tile_geom, 4326)
                              AND ST_Intersects(rb.simplified_geom, ST_Transform(bounds.tile_geom, 4326))
                        ),
                        mvtgeom AS (
                            SELECT
                                id::text AS region_id,
                                code,
                                name,
                                region_level,
                                country_code,
                                ST_AsMVTGeom(
                                    ST_Transform(clipped_geom, 3857),
                                    tile_geom,
                                    4096,
                                    64,
                                    true
                                ) AS geom
                            FROM mercator_safe
                            WHERE NOT ST_IsEmpty(clipped_geom)
                        )
                        SELECT COALESCE(ST_AsMVT(mvtgeom.*, 'regions', 4096, 'geom'), ''::bytea)
                        FROM mvtgeom
                        WHERE geom IS NOT NULL
                        """,
                        params,
                        byte[].class);
        return Objects.requireNonNullElseGet(tile, () -> new byte[0]);
    }

    private int executeUpsertBatch(List<BoundaryFeature> batch, double simplifyTolerance) {
        SqlParameterSource[] params =
                batch.stream()
                        .map(feature ->
                                new MapSqlParameterSource()
                                        .addValue("id", UUID.randomUUID())
                                        .addValue("regionLevel", feature.level().name())
                                        .addValue("code", feature.code())
                                        .addValue("countryCode", feature.countryCode())
                                        .addValue("name", feature.name())
                                        .addValue("geometryJson", feature.geometryJson())
                                        .addValue("tolerance", simplifyTolerance))
                        .toArray(SqlParameterSource[]::new);
        int[] counts = jdbcTemplate.batchUpdate(UPSERT_SQL, params);
        return Arrays.stream(counts)
                .map(count -> count == Statement.SUCCESS_NO_INFO ? 1 : Math.max(0, count))
                .sum();
    }
}
