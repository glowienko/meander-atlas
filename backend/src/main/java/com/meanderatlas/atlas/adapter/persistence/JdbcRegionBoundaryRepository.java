package com.meanderatlas.atlas.adapter.persistence;

import com.meanderatlas.atlas.domain.RegionLevel;
import com.meanderatlas.atlas.domain.boundaries.BoundaryFeature;
import com.meanderatlas.atlas.domain.boundaries.RegionBoundaryRepository;
import java.sql.Statement;
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

    private static final String UPSERT_SQL =
            """
            WITH mercator_bounds AS (
                SELECT ST_MakeEnvelope(-180.0, -85.05112878, 180.0, 85.05112878, 4326) AS env
            ),
            validated AS (
                SELECT
                    ST_Multi(
                        ST_CollectionExtract(
                            ST_MakeValid(ST_SetSRID(ST_GeomFromGeoJSON(:geometryJson), 4326)),
                            3
                        )
                    ) AS valid_geom
            ),
            clamped AS (
                SELECT
                    v.valid_geom,
                    CASE
                        WHEN ST_YMin(v.valid_geom) < -85.05112878
                          OR ST_YMax(v.valid_geom) > 85.05112878
                          OR ST_XMin(v.valid_geom) < -180.0
                          OR ST_XMax(v.valid_geom) > 180.0
                            THEN ST_Multi(
                                ST_CollectionExtract(
                                    ST_Intersection(v.valid_geom, mb.env),
                                    3
                                )
                            )
                        ELSE v.valid_geom
                    END AS mercator_geom
                FROM validated v
                CROSS JOIN mercator_bounds mb
                WHERE v.valid_geom IS NOT NULL AND NOT ST_IsEmpty(v.valid_geom)
            ),
            simplified AS (
                SELECT
                    c.valid_geom,
                    c.mercator_geom,
                    ST_Multi(
                        ST_CollectionExtract(
                            ST_SimplifyPreserveTopology(c.mercator_geom, :tolerance),
                            3
                        )
                    ) AS candidate_simplified
                FROM clamped c
            )
            INSERT INTO region_boundaries (id, region_level, code, country_code, name, geom, simplified_geom)
            SELECT
                CAST(:id AS uuid),
                :regionLevel,
                :code,
                :countryCode,
                :name,
                valid_geom,
                CASE
                    WHEN candidate_simplified IS NOT NULL AND NOT ST_IsEmpty(candidate_simplified)
                        THEN candidate_simplified
                    WHEN mercator_geom IS NOT NULL AND NOT ST_IsEmpty(mercator_geom)
                        THEN mercator_geom
                    ELSE valid_geom
                END
            FROM simplified
            ON CONFLICT (region_level, code) DO UPDATE
            SET country_code = EXCLUDED.country_code,
                name = EXCLUDED.name,
                geom = EXCLUDED.geom,
                simplified_geom = EXCLUDED.simplified_geom
            """;

    private static final String ASSIGN_REGIONS_SQL =
            """
            WITH resolved_country AS (
                SELECT
                    p.id,
                    p.geom,
                    p.admin1_id,
                    COALESCE(p.country_code, %s) AS next_country_code
                FROM places p
                WHERE p.geom IS NOT NULL
                  AND (p.country_code IS NULL OR p.admin1_id IS NULL)
            ),
            resolved AS (
                SELECT
                    rc.id,
                    rc.next_country_code,
                    COALESCE(rc.admin1_id, %s) AS next_admin1_id
                FROM resolved_country rc
            )
            UPDATE places p
            SET country_code = resolved.next_country_code,
                admin1_id = resolved.next_admin1_id,
                updated_at = now()
            FROM resolved
            WHERE p.id = resolved.id
              AND (
                  p.country_code IS DISTINCT FROM resolved.next_country_code
                  OR p.admin1_id IS DISTINCT FROM resolved.next_admin1_id
              )
            """
                    .formatted(
                            SpatialRegionSql.countryLookupSql("p.geom"),
                            SpatialRegionSql.admin1LookupSql("rc.geom", "rc.next_country_code"));

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
                ASSIGN_REGIONS_SQL,
                Map.of("coastalFallbackMeters", SpatialRegionSql.COASTAL_FALLBACK_METERS));
    }

    @Override
    public byte[] fetchRegionMvtTile(
            RegionLevel level, int z, int x, int y, @Nullable String countryCode) {
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("regionLevel", level.name())
                        .addValue("z", z)
                        .addValue("x", x)
                        .addValue("y", y);
        String countryFilter = countryCode == null ? "" : "AND rb.country_code = :countryCode";
        if (countryCode != null) {
            params.addValue("countryCode", countryCode);
        }
        byte[] tile =
                jdbcTemplate.queryForObject(
                        """
                        WITH bounds AS (
                            SELECT
                                ST_TileEnvelope(:z, :x, :y) AS tile_geom,
                                ST_Transform(
                                    ST_Intersection(
                                        ST_TileEnvelope(:z, :x, :y, margin => 64.0 / 4096.0),
                                        ST_TileEnvelope(0, 0, 0)
                                    ),
                                    4326
                                ) AS filter_geom
                        ),
                        mvtgeom AS (
                            SELECT
                                rb.id::text AS region_id,
                                rb.code,
                                rb.name,
                                rb.region_level,
                                rb.country_code,
                                ST_AsMVTGeom(
                                    ST_Transform(rb.simplified_geom, 3857),
                                    bounds.tile_geom,
                                    4096,
                                    64,
                                    true
                                ) AS geom
                            FROM region_boundaries rb
                            CROSS JOIN bounds
                            WHERE rb.region_level = :regionLevel
                              %s
                              AND ST_Intersects(rb.simplified_geom, bounds.filter_geom)
                        )
                        SELECT COALESCE(ST_AsMVT(mvtgeom.*, 'regions', 4096, 'geom'), ''::bytea)
                        FROM mvtgeom
                        WHERE geom IS NOT NULL
                        """
                                .formatted(countryFilter),
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
