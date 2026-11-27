ALTER TABLE places DROP CONSTRAINT IF EXISTS places_coord_source_check;
ALTER TABLE places
    ADD CONSTRAINT places_coord_source_check
    CHECK (coord_source IN ('TAKEOUT', 'TAKEOUT_S2_APPROX', 'TIMELINE', 'OPEN_CATALOG', 'GOOGLE_TEMP'));

CREATE INDEX idx_resolution_jobs_type_status_next_run
    ON resolution_jobs (job_type, status, next_run_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE UNIQUE INDEX uq_resolution_jobs_active_enrichment
    ON resolution_jobs (user_id, place_id)
    WHERE job_type = 'OPEN_CATALOG_ENRICH' AND status IN ('PENDING', 'IN_PROGRESS', 'FAILED');
