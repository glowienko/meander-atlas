UPDATE region_boundaries
SET simplified_geom = ST_Multi(
        ST_CollectionExtract(
            ST_MakeValid(
                ST_ClipByBox2D(
                    simplified_geom,
                    ST_MakeEnvelope(-180.0, -85.05, 180.0, 85.05, 4326)
                )
            ),
            3
        )
    )
WHERE simplified_geom IS NOT NULL
  AND (
      ST_YMin(simplified_geom) < -85.05
      OR ST_YMax(simplified_geom) > 85.05
  );
