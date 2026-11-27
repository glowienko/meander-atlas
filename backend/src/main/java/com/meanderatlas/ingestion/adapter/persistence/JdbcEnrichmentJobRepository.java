package com.meanderatlas.ingestion.adapter.persistence;

import com.meanderatlas.ingestion.domain.source.EnrichmentJobRepository;
import com.meanderatlas.ingestion.domain.source.ImportRecordKind;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
class JdbcEnrichmentJobRepository implements EnrichmentJobRepository {

    private static final int BATCH_SIZE = 500;

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
}
