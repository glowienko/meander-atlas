CREATE INDEX idx_region_boundaries_geog ON region_boundaries USING GIST ((geom::geography));
CREATE INDEX idx_places_geog ON places USING GIST ((geom::geography));
