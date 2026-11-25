package com.meanderatlas.atlas.adapter.persistence;

final class SpatialRegionSql {

    static final double COASTAL_FALLBACK_METERS = 15_000.0;

    private SpatialRegionSql() {}

    static String countryLookupSql(String pointExpr) {
        return """
        COALESCE(
            (
                SELECT rb.country_code
                FROM region_boundaries rb
                WHERE rb.region_level = 'COUNTRY'
                  AND %1$s IS NOT NULL
                  AND ST_Covers(rb.geom, %1$s)
                ORDER BY rb.code ASC
                LIMIT 1
            ),
            (
                SELECT rb.country_code
                FROM region_boundaries rb
                WHERE rb.region_level = 'COUNTRY'
                  AND %1$s IS NOT NULL
                  AND ST_DWithin(
                      rb.geom::geography,
                      (%1$s)::geography,
                      :coastalFallbackMeters)
                ORDER BY ST_Distance(rb.geom::geography, (%1$s)::geography) ASC,
                         rb.code ASC
                LIMIT 1
            )
        )
        """
                .formatted(pointExpr);
    }

    static String admin1LookupSql(String pointExpr, String resolvedCountryExpr) {
        return """
        COALESCE(
            (
                SELECT rb.id
                FROM region_boundaries rb
                WHERE rb.region_level = 'ADMIN1'
                  AND %1$s IS NOT NULL
                  AND ST_Covers(rb.geom, %1$s)
                ORDER BY (%2$s IS NOT NULL AND rb.country_code = %2$s) DESC,
                         rb.code ASC
                LIMIT 1
            ),
            (
                SELECT rb.id
                FROM region_boundaries rb
                WHERE rb.region_level = 'ADMIN1'
                  AND %1$s IS NOT NULL
                  AND (%2$s IS NULL OR rb.country_code = %2$s)
                  AND ST_DWithin(
                      rb.geom::geography,
                      (%1$s)::geography,
                      :coastalFallbackMeters)
                ORDER BY ST_Distance(rb.geom::geography, (%1$s)::geography) ASC,
                         rb.code ASC
                LIMIT 1
            )
        )
        """
                .formatted(pointExpr, resolvedCountryExpr);
    }
}
