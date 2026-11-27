ALTER TABLE region_boundaries
    ADD COLUMN focus_min_lng DOUBLE PRECISION,
    ADD COLUMN focus_min_lat DOUBLE PRECISION,
    ADD COLUMN focus_max_lng DOUBLE PRECISION,
    ADD COLUMN focus_max_lat DOUBLE PRECISION;

CREATE OR REPLACE FUNCTION compute_region_focus_bbox() RETURNS TRIGGER AS $$
DECLARE
    bbox RECORD;
BEGIN
    IF NEW.simplified_geom IS NULL OR ST_IsEmpty(NEW.simplified_geom) THEN
        NEW.focus_min_lng := NULL;
        NEW.focus_min_lat := NULL;
        NEW.focus_max_lng := NULL;
        NEW.focus_max_lat := NULL;
        RETURN NEW;
    END IF;

    IF ST_NumGeometries(NEW.simplified_geom) <= 1 THEN
        NEW.focus_min_lng := ST_XMin(NEW.simplified_geom);
        NEW.focus_min_lat := ST_YMin(NEW.simplified_geom);
        NEW.focus_max_lng := ST_XMax(NEW.simplified_geom);
        NEW.focus_max_lat := ST_YMax(NEW.simplified_geom);
        RETURN NEW;
    END IF;

    WITH parts AS (
        SELECT (ST_Dump(NEW.simplified_geom)).geom AS part_geom
    ),
    ranked AS (
        SELECT
            part_geom,
            ST_Centroid(part_geom)::geography AS part_centroid,
            ROW_NUMBER() OVER (ORDER BY ST_Area(part_geom::geography) DESC) AS rn
        FROM parts
    ),
    primary_part AS (
        SELECT part_centroid AS main_centroid FROM ranked WHERE rn = 1
    ),
    clustered AS (
        SELECT ST_Collect(r.part_geom) AS focused_geom
        FROM ranked r
        CROSS JOIN primary_part pp
        WHERE r.rn = 1
           OR ST_DWithin(r.part_centroid, pp.main_centroid, 2000000.0)
    )
    SELECT
        ST_XMin(focused_geom) AS min_lng,
        ST_YMin(focused_geom) AS min_lat,
        ST_XMax(focused_geom) AS max_lng,
        ST_YMax(focused_geom) AS max_lat
    INTO bbox
    FROM clustered;

    NEW.focus_min_lng := COALESCE(bbox.min_lng, ST_XMin(NEW.simplified_geom));
    NEW.focus_min_lat := COALESCE(bbox.min_lat, ST_YMin(NEW.simplified_geom));
    NEW.focus_max_lng := COALESCE(bbox.max_lng, ST_XMax(NEW.simplified_geom));
    NEW.focus_max_lat := COALESCE(bbox.max_lat, ST_YMax(NEW.simplified_geom));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_region_boundaries_focus_bbox
    BEFORE INSERT OR UPDATE OF simplified_geom ON region_boundaries
    FOR EACH ROW
    EXECUTE FUNCTION compute_region_focus_bbox();

UPDATE region_boundaries SET simplified_geom = simplified_geom;
