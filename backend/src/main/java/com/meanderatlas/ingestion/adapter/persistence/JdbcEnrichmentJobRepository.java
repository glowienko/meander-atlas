package com.meanderatlas.ingestion.adapter.persistence;

import com.meanderatlas.ingestion.domain.source.EnrichmentJobRepository;
import com.meanderatlas.ingestion.domain.source.ImportRecordKind;
import com.meanderatlas.ingestion.domain.source.ResolutionJobType;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.sql.Array;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
class JdbcEnrichmentJobRepository implements EnrichmentJobRepository {

    private static final int BATCH_SIZE = 500;
    private static final int MAX_ERROR_LENGTH = 500;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcEnrichmentJobRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<PendingCoordinateJob> findPendingCoordinateLookups(
            UserId userId, @Nullable UUID afterJobId, int limit) {
        return jdbcTemplate.query(
                """
                SELECT
                    rj.id AS job_id,
                    rj.import_record_id,
                    ir.account_label,
                    ir.record_type,
                    ir.raw_title,
                    ir.raw_url,
                    ir.google_cid,
                    ir.observed_at,
                    ir.payload_json::text AS payload_json
                FROM resolution_jobs rj
                JOIN import_records ir ON ir.id = rj.import_record_id
                WHERE rj.user_id = :userId
                  AND rj.job_type = 'COORDINATE_LOOKUP'
                  AND rj.status = 'PENDING'
                  AND (CAST(:afterJobId AS uuid) IS NULL OR rj.id > CAST(:afterJobId AS uuid))
                ORDER BY rj.id ASC
                LIMIT :limit
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId.value())
                        .addValue(
                                "afterJobId",
                                afterJobId == null ? null : afterJobId.toString(),
                                Types.VARCHAR)
                        .addValue("limit", limit),
                (rs, _) -> {
                    OffsetDateTime observedOdt = rs.getObject("observed_at", OffsetDateTime.class);
                    Instant observedAt = observedOdt == null ? null : observedOdt.toInstant();
                    return new PendingCoordinateJob(
                            rs.getObject("job_id", UUID.class),
                            rs.getObject("import_record_id", UUID.class),
                            rs.getString("account_label"),
                            ImportRecordKind.valueOf(rs.getString("record_type")),
                            rs.getString("raw_title"),
                            rs.getString("raw_url"),
                            rs.getObject("google_cid", Long.class),
                            observedAt,
                            rs.getString("payload_json"));
                });
    }

    @Override
    public void markCoordinateJobCompleted(UUID jobId, PlaceId placeId) {
        jdbcTemplate.update(
                """
                UPDATE resolution_jobs
                SET status = 'COMPLETED',
                    place_id = :placeId,
                    updated_at = now()
                WHERE id = :jobId
                """,
                Map.of("jobId", jobId, "placeId", placeId.value()));
    }

    @Override
    public int countPendingCoordinateLookups(UserId userId) {
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)::int
                        FROM resolution_jobs
                        WHERE user_id = :userId
                          AND job_type = 'COORDINATE_LOOKUP'
                          AND status = 'PENDING'
                        """,
                        Map.of("userId", userId.value()),
                        Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public int enqueueOpenCatalogEnrichments(UserId userId, List<PlaceId> placeIds) {
        if (placeIds.isEmpty()) {
            return 0;
        }
        List<PlaceId> distinctPlaceIds = placeIds.stream().distinct().toList();
        return IntStream.iterate(0, i -> i < distinctPlaceIds.size(), i -> i + BATCH_SIZE)
                .mapToObj(start ->
                        distinctPlaceIds.subList(
                                start, Math.min(start + BATCH_SIZE, distinctPlaceIds.size())))
                .mapToInt(chunk -> enqueueEnrichmentChunk(userId, chunk))
                .sum();
    }

    private int enqueueEnrichmentChunk(UserId userId, List<PlaceId> chunk) {
        SqlParameterSource[] params =
                chunk.stream()
                        .map(placeId ->
                                new MapSqlParameterSource()
                                        .addValue("id", UUID.randomUUID())
                                        .addValue("userId", userId.value())
                                        .addValue("placeId", placeId.value()))
                        .toArray(SqlParameterSource[]::new);
        int[] updated =
                jdbcTemplate.batchUpdate(
                        """
                        INSERT INTO resolution_jobs (
                            id, user_id, import_record_id, place_id, job_type, status, attempts
                        )
                        SELECT
                            CAST(:id AS uuid),
                            CAST(:userId AS uuid),
                            NULL,
                            CAST(:placeId AS uuid),
                            'OPEN_CATALOG_ENRICH',
                            'PENDING',
                            0
                        WHERE NOT EXISTS (
                            SELECT 1 FROM resolution_jobs r
                            WHERE r.user_id = CAST(:userId AS uuid)
                              AND r.place_id = CAST(:placeId AS uuid)
                              AND r.job_type = 'OPEN_CATALOG_ENRICH'
                        )
                        ON CONFLICT DO NOTHING
                        """,
                        params);
        return Arrays.stream(updated).filter(count -> count > 0).sum();
    }

    @Override
    public List<ClaimedResolutionJob> claimPendingEnrichmentJobs(UserId userId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<UUID> claimedIds =
                jdbcTemplate.query(
                        """
                        WITH claimed AS (
                            SELECT id
                            FROM resolution_jobs
                            WHERE user_id = :userId
                              AND job_type IN ('OPEN_CATALOG_ENRICH', 'COORDINATE_LOOKUP')
                              AND status IN ('PENDING', 'FAILED')
                              AND next_run_at <= now()
                            ORDER BY (job_type = 'OPEN_CATALOG_ENRICH') DESC, next_run_at ASC, id ASC
                            FOR UPDATE SKIP LOCKED
                            LIMIT :limit
                        )
                        UPDATE resolution_jobs rj
                        SET status = 'IN_PROGRESS',
                            attempts = rj.attempts + 1,
                            updated_at = now()
                        FROM claimed
                        WHERE rj.id = claimed.id
                        RETURNING rj.id
                        """,
                        Map.of("userId", userId.value(), "limit", limit),
                        (rs, _) -> Objects.requireNonNull(rs.getObject("id", UUID.class)));
        if (claimedIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT
                    rj.id AS job_id,
                    rj.job_type,
                    rj.place_id,
                    rj.attempts,
                    rj.import_record_id,
                    ir.account_label,
                    ir.record_type,
                    ir.raw_title,
                    ir.raw_url,
                    ir.google_cid,
                    ir.observed_at,
                    ir.payload_json::text AS payload_json
                FROM resolution_jobs rj
                LEFT JOIN import_records ir ON ir.id = rj.import_record_id
                WHERE rj.id = ANY(:jobIds)
                ORDER BY (rj.job_type = 'OPEN_CATALOG_ENRICH') DESC, rj.next_run_at ASC, rj.id ASC
                """,
                new MapSqlParameterSource("jobIds", uuidArray(claimedIds)),
                (rs, _) -> {
                    UUID jobId = Objects.requireNonNull(rs.getObject("job_id", UUID.class));
                    ResolutionJobType jobType =
                            ResolutionJobType.valueOf(
                                    Objects.requireNonNull(rs.getString("job_type")));
                    UUID rawPlaceId = rs.getObject("place_id", UUID.class);
                    PlaceId placeId = rawPlaceId == null ? null : new PlaceId(rawPlaceId);
                    int attempts = rs.getInt("attempts");
                    UUID importRecordId = rs.getObject("import_record_id", UUID.class);
                    PendingCoordinateJob coordinateJob = null;
                    if (importRecordId != null) {
                        OffsetDateTime observedOdt =
                                rs.getObject("observed_at", OffsetDateTime.class);
                        Instant observedAt = observedOdt == null ? null : observedOdt.toInstant();
                        coordinateJob =
                                new PendingCoordinateJob(
                                        jobId,
                                        importRecordId,
                                        Objects.requireNonNull(rs.getString("account_label")),
                                        ImportRecordKind.valueOf(
                                                Objects.requireNonNull(
                                                        rs.getString("record_type"))),
                                        rs.getString("raw_title"),
                                        rs.getString("raw_url"),
                                        rs.getObject("google_cid", Long.class),
                                        observedAt,
                                        Objects.requireNonNull(rs.getString("payload_json")));
                    }
                    return new ClaimedResolutionJob(
                            jobId, jobType, placeId, attempts, coordinateJob);
                });
    }

    @Override
    public void markJobCompleted(UUID jobId, @Nullable PlaceId placeId) {
        jdbcTemplate.update(
                """
                UPDATE resolution_jobs
                SET status = 'COMPLETED',
                    place_id = COALESCE(CAST(:placeId AS uuid), place_id),
                    last_error = NULL,
                    updated_at = now()
                WHERE id = :jobId
                """,
                new MapSqlParameterSource()
                        .addValue("jobId", jobId)
                        .addValue(
                                "placeId", placeId == null ? null : placeId.value(), Types.OTHER));
    }

    @Override
    public void markJobFailed(UUID jobId, String error, Instant nextRunAt) {
        jdbcTemplate.update(
                """
                UPDATE resolution_jobs
                SET status = 'FAILED',
                    last_error = :lastError,
                    next_run_at = CAST(:nextRunAt AS timestamptz),
                    updated_at = now()
                WHERE id = :jobId
                """,
                new MapSqlParameterSource()
                        .addValue("jobId", jobId)
                        .addValue("lastError", truncateError(error))
                        .addValue(
                                "nextRunAt",
                                OffsetDateTime.ofInstant(nextRunAt, ZoneOffset.UTC),
                                Types.TIMESTAMP_WITH_TIMEZONE));
    }

    @Override
    public void markJobSkipped(UUID jobId, String reason) {
        jdbcTemplate.update(
                """
                UPDATE resolution_jobs
                SET status = 'SKIPPED',
                    last_error = :lastError,
                    updated_at = now()
                WHERE id = :jobId
                """,
                new MapSqlParameterSource()
                        .addValue("jobId", jobId)
                        .addValue("lastError", truncateError(reason)));
    }

    @Override
    public void releaseOnDryRun(List<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return;
        }
        jdbcTemplate.update(
                """
                UPDATE resolution_jobs
                SET status = CASE WHEN attempts <= 1 THEN 'PENDING' ELSE 'FAILED' END,
                    attempts = GREATEST(0, attempts - 1),
                    updated_at = now()
                WHERE id = ANY(:jobIds)
                  AND status = 'IN_PROGRESS'
                """,
                new MapSqlParameterSource("jobIds", uuidArray(jobIds)));
    }

    @Override
    public int countRemainingPendingJobs(UserId userId) {
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)::int
                        FROM resolution_jobs
                        WHERE user_id = :userId
                          AND job_type IN ('OPEN_CATALOG_ENRICH', 'COORDINATE_LOOKUP')
                          AND status IN ('PENDING', 'FAILED')
                        """,
                        Map.of("userId", userId.value()),
                        Integer.class);
        return count == null ? 0 : count;
    }

    private static SqlTypeValue uuidArray(List<UUID> ids) {
        UUID[] values = ids.toArray(UUID[]::new);
        return (ps, paramIndex, sqlType, typeName) -> {
            Array sqlArray = ps.getConnection().createArrayOf("uuid", values);
            ps.setArray(paramIndex, sqlArray);
        };
    }

    private static String truncateError(String raw) {
        if (raw.length() <= MAX_ERROR_LENGTH) {
            return raw;
        }
        return raw.substring(0, MAX_ERROR_LENGTH);
    }
}
