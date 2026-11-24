CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE region_boundaries (
    id UUID PRIMARY KEY,
    region_level TEXT NOT NULL CHECK (region_level IN ('COUNTRY', 'ADMIN1', 'CITY')),
    code TEXT NOT NULL,
    country_code TEXT NOT NULL,
    name TEXT NOT NULL,
    geom GEOMETRY(MultiPolygon, 4326) NOT NULL,
    simplified_geom GEOMETRY(MultiPolygon, 4326) NOT NULL,
    UNIQUE (region_level, code)
);

CREATE INDEX idx_region_boundaries_geom ON region_boundaries USING GIST (geom);
CREATE INDEX idx_region_boundaries_simplified_geom ON region_boundaries USING GIST (simplified_geom);

CREATE TABLE places (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    canonical_name TEXT NOT NULL,
    geom GEOMETRY(Point, 4326),
    coord_source TEXT CHECK (coord_source IN ('TAKEOUT', 'TIMELINE', 'OPEN_CATALOG', 'GOOGLE_TEMP')),
    coord_expires_at TIMESTAMPTZ,
    google_place_id TEXT,
    google_cid BIGINT,
    osm_id TEXT,
    overture_id TEXT,
    category TEXT NOT NULL CHECK (category IN (
        'RESTAURANT',
        'CAFE_BAR',
        'HOTEL',
        'MUSEUM',
        'LANDMARK',
        'SACRAL',
        'MOUNTAIN',
        'WATER',
        'NATURE_PARK',
        'VIEWPOINT',
        'UNCATEGORIZED'
    )),
    subcategory TEXT,
    category_source TEXT NOT NULL CHECK (category_source IN ('HEURISTIC', 'OPEN_CATALOG', 'MANUAL')),
    derived_role TEXT NOT NULL CHECK (derived_role IN ('PREFERRED_HOTEL', 'MUST_SEE', 'FAVORITE', 'BACKLOG')),
    country_code TEXT,
    admin1_id UUID REFERENCES region_boundaries(id),
    city_name TEXT,
    opening_hours_json JSONB,
    photo_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (coord_source IS DISTINCT FROM 'GOOGLE_TEMP' OR coord_expires_at IS NOT NULL)
);

CREATE INDEX idx_places_geom ON places USING GIST (geom);
CREATE INDEX idx_places_user_id ON places (user_id);
CREATE INDEX idx_places_canonical_name_trgm ON places USING GIN (canonical_name gin_trgm_ops);
CREATE UNIQUE INDEX uq_places_user_google_place_id ON places (user_id, google_place_id) WHERE google_place_id IS NOT NULL;
CREATE UNIQUE INDEX uq_places_user_google_cid ON places (user_id, google_cid) WHERE google_cid IS NOT NULL;

CREATE TABLE place_visit_state (
    place_id UUID PRIMARY KEY REFERENCES places(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('UNVISITED', 'VISITED', 'NEEDS_TRIAGE')),
    source TEXT CHECK (source IN ('TIMELINE', 'REVIEW', 'MANUAL_SINGLE', 'MANUAL_BULK')),
    visit_count INT NOT NULL DEFAULT 0 CHECK (visit_count >= 0),
    first_visited_at TIMESTAMPTZ,
    last_visited_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (
        (status = 'UNVISITED' AND visit_count = 0 AND first_visited_at IS NULL AND last_visited_at IS NULL)
        OR (status = 'NEEDS_TRIAGE' AND source IS NOT NULL)
        OR (status = 'VISITED' AND source IS NOT NULL AND visit_count >= 1)
    )
);

CREATE INDEX idx_place_visit_state_user_status ON place_visit_state (user_id, status);

CREATE TABLE place_memberships (
    id UUID PRIMARY KEY,
    place_id UUID NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    account_label TEXT NOT NULL,
    list_name TEXT NOT NULL,
    list_kind TEXT NOT NULL CHECK (list_kind IN ('STARRED', 'FAVORITE', 'WANT_TO_GO', 'CUSTOM', 'REVIEW')),
    note TEXT,
    tags TEXT,
    source_url TEXT,
    saved_at TIMESTAMPTZ,
    UNIQUE (place_id, account_label, list_name)
);

CREATE INDEX idx_place_memberships_user_account ON place_memberships (user_id, account_label);

CREATE TABLE import_batches (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    account_label TEXT NOT NULL,
    content_sha256 TEXT NOT NULL,
    source_type TEXT NOT NULL CHECK (source_type IN ('LOCAL_DIRECTORY', 'WEB_UPLOAD', 'GOOGLE_DRIVE')),
    status TEXT NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    record_count INT NOT NULL DEFAULT 0 CHECK (record_count >= 0),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    UNIQUE (user_id, account_label, content_sha256)
);

CREATE TABLE import_records (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES import_batches(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    account_label TEXT NOT NULL,
    record_type TEXT NOT NULL CHECK (record_type IN ('SAVED_CSV', 'STARRED_GEOJSON', 'REVIEW_GEOJSON', 'TIMELINE_VISIT')),
    source_file TEXT NOT NULL,
    raw_title TEXT,
    raw_url TEXT,
    google_place_id TEXT,
    google_cid BIGINT,
    geom GEOMETRY(Point, 4326),
    observed_at TIMESTAMPTZ,
    payload_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_import_records_batch_id ON import_records (batch_id);
CREATE INDEX idx_import_records_user_cid ON import_records (user_id, google_cid) WHERE google_cid IS NOT NULL;

CREATE TABLE resolution_jobs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    import_record_id UUID REFERENCES import_records(id) ON DELETE CASCADE,
    place_id UUID REFERENCES places(id) ON DELETE CASCADE,
    job_type TEXT NOT NULL CHECK (job_type IN ('COORDINATE_LOOKUP', 'OPEN_CATALOG_ENRICH')),
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'SKIPPED')),
    attempts INT NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error TEXT,
    next_run_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_resolution_jobs_pending_queue ON resolution_jobs (next_run_at) WHERE status = 'PENDING';
