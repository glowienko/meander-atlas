package com.meanderatlas.ingestion.adapter.persistence;

import com.meanderatlas.ingestion.domain.source.ImportRepository;
import com.meanderatlas.ingestion.domain.source.ImportStatusSummary;
import com.meanderatlas.ingestion.domain.source.NewImportRecord;
import com.meanderatlas.ingestion.domain.source.NewResolutionJob;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.UserId;
import java.sql.Array;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
class JdbcImportRepository implements ImportRepository {

    private static final int BATCH_SIZE = 500;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcImportRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean hasCompletedBatch(UserId userId, String accountLabel, String contentSha256) {
        Boolean exists =
                jdbcTemplate.queryForObject(
                        """
                        SELECT EXISTS(
                            SELECT 1 FROM import_batches
                            WHERE user_id = :userId
                              AND account_label = :accountLabel
                              AND content_sha256 = :contentSha256
                              AND status = 'COMPLETED'
                        )
                        """,
                        Map.of(
                                "userId",
                                userId.value(),
                                "accountLabel",
                                accountLabel,
                                "contentSha256",
                                contentSha256),
                        Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public UUID startOrRestartBatch(
            UserId userId, String accountLabel, String contentSha256, String sourceType) {
        UUID batchId =
                Objects.requireNonNull(
                        jdbcTemplate.queryForObject(
                                """
                                INSERT INTO import_batches (
                                    id, user_id, account_label, content_sha256, source_type, status, record_count
                                )
                                VALUES (
                                    CAST(:id AS uuid),
                                    CAST(:userId AS uuid),
                                    :accountLabel,
                                    :contentSha256,
                                    :sourceType,
                                    'RUNNING',
                                    0
                                )
                                ON CONFLICT (user_id, account_label, content_sha256) DO UPDATE
                                SET source_type = EXCLUDED.source_type,
                                    status = 'RUNNING',
                                    record_count = 0,
                                    started_at = now(),
                                    completed_at = NULL
                                RETURNING id
                                """,
                                Map.of(
                                        "id",
                                        UUID.randomUUID(),
                                        "userId",
                                        userId.value(),
                                        "accountLabel",
                                        accountLabel,
                                        "contentSha256",
                                        contentSha256,
                                        "sourceType",
                                        sourceType),
                                UUID.class));
        jdbcTemplate.update(
                "DELETE FROM import_records WHERE batch_id = :batchId", Map.of("batchId", batchId));
        return batchId;
    }

    @Override
    public void insertRecords(
            UUID batchId, UserId userId, String accountLabel, List<NewImportRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        IntStream.iterate(0, i -> i < records.size(), i -> i + BATCH_SIZE)
                .mapToObj(start ->
                        records.subList(start, Math.min(start + BATCH_SIZE, records.size())))
                .forEach(chunk -> insertRecordChunk(batchId, userId, accountLabel, chunk));
    }

    @Override
    public void enqueueCoordinateLookups(UserId userId, List<NewResolutionJob> jobs) {
        if (jobs.isEmpty()) {
            return;
        }
        IntStream.iterate(0, i -> i < jobs.size(), i -> i + BATCH_SIZE)
                .mapToObj(start -> jobs.subList(start, Math.min(start + BATCH_SIZE, jobs.size())))
                .forEach(chunk -> insertJobChunk(userId, chunk));
    }

    @Override
    public void markBatchCompleted(UUID batchId, int recordCount) {
        jdbcTemplate.update(
                """
                UPDATE import_batches
                SET status = 'COMPLETED',
                    record_count = :recordCount,
                    completed_at = now()
                WHERE id = :batchId
                """,
                Map.of("batchId", batchId, "recordCount", recordCount));
    }

    @Override
    public ImportStatusSummary fetchStatus(UserId userId) {
        return Objects.requireNonNull(
                jdbcTemplate.queryForObject(
                        """
                        SELECT
                            (SELECT COUNT(*)::int
                             FROM resolution_jobs
                             WHERE user_id = :userId
                               AND job_type = 'COORDINATE_LOOKUP'
                               AND status IN ('PENDING', 'FAILED')) AS pending_coordinate_count,
                            (SELECT COUNT(*)::int
                             FROM resolution_jobs
                             WHERE user_id = :userId
                               AND job_type = 'OPEN_CATALOG_ENRICH'
                               AND status IN ('PENDING', 'FAILED')) AS pending_enrichment_count,
                            (SELECT COUNT(*)::int
                             FROM places
                             WHERE user_id = :userId
                               AND geom IS NOT NULL
                               AND (
                                   coord_source = 'OPEN_CATALOG'
                                   OR category_source = 'OPEN_CATALOG'
                                   OR photo_url IS NOT NULL
                                   OR osm_id IS NOT NULL
                               )) AS enriched_place_count,
                            (SELECT COUNT(*)::int
                             FROM places
                             WHERE user_id = :userId
                               AND geom IS NOT NULL
                               AND coord_source = 'TAKEOUT_S2_APPROX') AS approximate_place_count,
                            (SELECT COUNT(*)::int
                             FROM resolution_jobs
                             WHERE user_id = :userId
                               AND status IN ('FAILED', 'SKIPPED')) AS failed_or_unresolvable_count,
                            COUNT(*)::int AS completed_batches,
                            COALESCE(
                                ARRAY_AGG(DISTINCT account_label ORDER BY account_label ASC),
                                ARRAY[]::text[]
                            ) AS accounts,
                            MAX(completed_at) AS last_imported_at
                        FROM import_batches
                        WHERE user_id = :userId
                          AND status = 'COMPLETED'
                        """,
                        Map.of("userId", userId.value()),
                        (rs, _) -> {
                            Array accountsArray = rs.getArray("accounts");
                            List<String> accounts =
                                    accountsArray != null
                                                    && accountsArray.getArray()
                                                            instanceof String[] names
                                            ? List.of(names)
                                            : List.of();
                            OffsetDateTime lastImportedOdt =
                                    rs.getObject("last_imported_at", OffsetDateTime.class);
                            Instant lastImportedAt =
                                    lastImportedOdt == null ? null : lastImportedOdt.toInstant();
                            int pendingCoordinates = rs.getInt("pending_coordinate_count");
                            return new ImportStatusSummary(
                                    pendingCoordinates,
                                    pendingCoordinates,
                                    rs.getInt("pending_enrichment_count"),
                                    rs.getInt("enriched_place_count"),
                                    rs.getInt("approximate_place_count"),
                                    rs.getInt("failed_or_unresolvable_count"),
                                    rs.getInt("completed_batches"),
                                    accounts,
                                    lastImportedAt);
                        }));
    }

    private void insertRecordChunk(
            UUID batchId, UserId userId, String accountLabel, List<NewImportRecord> chunk) {
        SqlParameterSource[] params =
                chunk.stream()
                        .map(record -> {
                            GeoPoint coords = record.coordinates();
                            return new MapSqlParameterSource()
                                    .addValue("id", record.id())
                                    .addValue("batchId", batchId)
                                    .addValue("userId", userId.value())
                                    .addValue("accountLabel", accountLabel)
                                    .addValue("recordType", record.recordType().name())
                                    .addValue("sourceFile", record.sourceFile())
                                    .addValue("rawTitle", record.rawTitle(), Types.VARCHAR)
                                    .addValue("rawUrl", record.rawUrl(), Types.VARCHAR)
                                    .addValue(
                                            "googlePlaceId", record.googlePlaceId(), Types.VARCHAR)
                                    .addValue("googleCid", record.googleCid(), Types.BIGINT)
                                    .addValue("hasGeom", coords != null)
                                    .addValue("lng", coords == null ? 0.0 : coords.longitude())
                                    .addValue("lat", coords == null ? 0.0 : coords.latitude())
                                    .addValue(
                                            "observedAt",
                                            record.observedAt() == null
                                                    ? null
                                                    : OffsetDateTime.ofInstant(
                                                            record.observedAt(), ZoneOffset.UTC),
                                            Types.TIMESTAMP_WITH_TIMEZONE)
                                    .addValue("payloadJson", record.payloadJson());
                        })
                        .toArray(SqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO import_records (
                    id, batch_id, user_id, account_label, record_type, source_file,
                    raw_title, raw_url, google_place_id, google_cid, geom, observed_at, payload_json
                )
                VALUES (
                    CAST(:id AS uuid),
                    CAST(:batchId AS uuid),
                    CAST(:userId AS uuid),
                    :accountLabel,
                    :recordType,
                    :sourceFile,
                    CAST(:rawTitle AS TEXT),
                    CAST(:rawUrl AS TEXT),
                    CAST(:googlePlaceId AS TEXT),
                    CAST(:googleCid AS BIGINT),
                    CASE
                        WHEN :hasGeom THEN ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)
                        ELSE NULL
                    END,
                    CAST(:observedAt AS timestamptz),
                    CAST(:payloadJson AS jsonb)
                )
                """,
                params);
    }

    private void insertJobChunk(UserId userId, List<NewResolutionJob> chunk) {
        SqlParameterSource[] params =
                chunk.stream()
                        .map(job ->
                                new MapSqlParameterSource()
                                        .addValue("id", job.id())
                                        .addValue("userId", userId.value())
                                        .addValue("importRecordId", job.importRecordId()))
                        .toArray(SqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO resolution_jobs (
                    id, user_id, import_record_id, place_id, job_type, status, attempts
                )
                VALUES (
                    CAST(:id AS uuid),
                    CAST(:userId AS uuid),
                    CAST(:importRecordId AS uuid),
                    NULL,
                    'COORDINATE_LOOKUP',
                    'PENDING',
                    0
                )
                """,
                params);
    }
}
