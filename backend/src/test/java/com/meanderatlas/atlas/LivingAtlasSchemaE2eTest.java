package com.meanderatlas.atlas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meanderatlas.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Tag("e2e")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LivingAtlasSchemaE2eTest {

    private static final List<String> EXPECTED_TABLES =
            List.of(
                    "region_boundaries",
                    "places",
                    "place_visit_state",
                    "place_memberships",
                    "import_batches",
                    "imported_signals",
                    "resolution_jobs");
    private static final List<String> EXPECTED_INDEXES =
            List.of(
                    "idx_region_boundaries_geom",
                    "idx_region_boundaries_simplified_geom",
                    "idx_places_geom",
                    "idx_places_canonical_name_trgm",
                    "uq_places_user_google_place_id",
                    "uq_places_user_google_cid",
                    "idx_resolution_jobs_pending_queue");

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final double KRAKOW_LNG = 19.935412;
    private static final double KRAKOW_LAT = 50.054049;
    private static final long SAMPLE_CID = 536870913L;
    private static final double MIN_SIMILARITY = 0.45;
    private static final String SAMPLE_POLYGON_WKT =
            "MULTIPOLYGON(((19.0 49.5, 21.0 49.5, 21.0 50.5, 19.0 50.5, 19.0 49.5)))";

    @Autowired private NamedParameterJdbcTemplate jdbc;

    @Test
    void migratesAllLivingAtlasTablesIndexesAndPgTrgm() {
        List<@Nullable String> actualTables =
                jdbc.queryForList(
                        """
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name IN (:tables)
                        """,
                        Map.of("tables", EXPECTED_TABLES),
                        String.class);
        List<@Nullable String> actualIndexes =
                jdbc.queryForList(
                        """
                        SELECT indexname
                        FROM pg_indexes
                        WHERE schemaname = 'public'
                          AND indexname IN (:indexes)
                        """,
                        Map.of("indexes", EXPECTED_INDEXES),
                        String.class);
        Double trigramScore =
                jdbc.queryForObject(
                        "SELECT similarity(:leftTitle, :rightTitle)",
                        Map.of(
                                "leftTitle", "Wawel Royal Castle",
                                "rightTitle", "Wawel Castle"),
                        Double.class);

        assertThat(actualTables).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
        assertThat(actualIndexes).containsExactlyInAnyOrderElementsOf(EXPECTED_INDEXES);
        assertThat(trigramScore).isNotNull().isGreaterThanOrEqualTo(MIN_SIMILARITY);
    }

    @Test
    void enforcesSpatialContainmentForeignKeysAndCheckConstraints() {
        UUID regionId = UUID.randomUUID();
        UUID placeId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        UUID signalId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        jdbc.update(
                """
                INSERT INTO region_boundaries
                    (id, region_level, code, country_code, name, parent_country_code, geom, simplified_geom)
                VALUES
                    (:id, 'ADMIN1', 'PL-12', 'PL', 'Malopolskie', 'PL',
                     ST_GeomFromText(:wkt, 4326), ST_GeomFromText(:wkt, 4326))
                """,
                Map.of("id", regionId, "wkt", SAMPLE_POLYGON_WKT));

        jdbc.update(
                """
                INSERT INTO places
                    (id, user_id, canonical_name, geom, coord_source, google_place_id, google_cid,
                     category, category_source, derived_role, derived_priority, country_code, admin1_id)
                VALUES
                    (:id, :userId, 'Wawel Royal Castle',
                     ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), 'TAKEOUT',
                     'ChIJ-wawel-schema-test', :cid,
                     'MUSEUM', 'HEURISTIC', 'MUST_SEE', 30, 'PL', :admin1Id)
                """,
                new MapSqlParameterSource()
                        .addValue("id", placeId)
                        .addValue("userId", USER_ID)
                        .addValue("lng", KRAKOW_LNG)
                        .addValue("lat", KRAKOW_LAT)
                        .addValue("cid", SAMPLE_CID)
                        .addValue("admin1Id", regionId));

        Boolean contained =
                jdbc.queryForObject(
                        """
                        SELECT ST_Contains(r.geom, p.geom)
                        FROM region_boundaries r
                        JOIN places p ON p.admin1_id = r.id
                        WHERE p.id = :placeId
                        """,
                        Map.of("placeId", placeId),
                        Boolean.class);
        assertThat(contained).isTrue();

        jdbc.update(
                """
                INSERT INTO place_visit_state (place_id, user_id, status, source, visit_count)
                VALUES (:placeId, :userId, 'VISITED', 'REVIEW', 1)
                """,
                Map.of("placeId", placeId, "userId", USER_ID));

        jdbc.update(
                """
                INSERT INTO place_memberships
                    (id, place_id, user_id, account_label, list_name, list_kind)
                VALUES
                    (:id, :placeId, :userId, 'account-1', 'Starred', 'STARRED')
                """,
                Map.of("id", UUID.randomUUID(), "placeId", placeId, "userId", USER_ID));

        jdbc.update(
                """
                INSERT INTO import_batches
                    (id, user_id, account_label, content_sha256, source_type, status, signal_count)
                VALUES
                    (:id, :userId, 'account-1', 'sha256-schema-test', 'LOCAL_DIRECTORY', 'COMPLETED', 1)
                """,
                Map.of("id", batchId, "userId", USER_ID));

        jdbc.update(
                """
                INSERT INTO imported_signals
                    (id, batch_id, user_id, account_label, signal_type, source_file, payload_json)
                VALUES
                    (:id, :batchId, :userId, 'account-1', 'SAVED_CSV', 'Want to go.csv', '{}'::jsonb)
                """,
                Map.of("id", signalId, "batchId", batchId, "userId", USER_ID));

        jdbc.update(
                """
                INSERT INTO resolution_jobs
                    (id, user_id, signal_id, place_id, job_type, status, attempts)
                VALUES
                    (:id, :userId, :signalId, :placeId, 'OPEN_CATALOG_ENRICH', 'PENDING', 0)
                """,
                Map.of("id", jobId, "userId", USER_ID, "signalId", signalId, "placeId", placeId));

        UUID claimedJobId =
                jdbc.queryForObject(
                        """
                        SELECT id FROM resolution_jobs
                        WHERE status = 'PENDING' AND next_run_at <= now()
                        ORDER BY next_run_at ASC
                        FOR UPDATE SKIP LOCKED
                        LIMIT 1
                        """,
                        Map.of(),
                        UUID.class);
        assertThat(claimedJobId).isEqualTo(jobId);

        assertThatThrownBy(() ->
                        jdbc.update(
                                """
                                INSERT INTO places
                                    (id, user_id, canonical_name, category, category_source, derived_role)
                                VALUES
                                    (:id, :userId, 'Bad Category', 'INVALID', 'HEURISTIC', 'BACKLOG')
                                """,
                                Map.of("id", UUID.randomUUID(), "userId", USER_ID)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() ->
                        jdbc.update(
                                """
                                INSERT INTO places
                                    (id, user_id, canonical_name, google_place_id,
                                     category, category_source, derived_role)
                                VALUES
                                    (:id, :userId, 'Duplicate PlaceId', 'ChIJ-wawel-schema-test',
                                     'UNCATEGORIZED', 'HEURISTIC', 'BACKLOG')
                                """,
                                Map.of("id", UUID.randomUUID(), "userId", USER_ID)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() ->
                        jdbc.update(
                                """
                                UPDATE place_visit_state
                                SET visit_count = 0
                                WHERE place_id = :placeId
                                """,
                                Map.of("placeId", placeId)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() ->
                        jdbc.update(
                                """
                                INSERT INTO places
                                    (id, user_id, canonical_name, coord_source,
                                     category, category_source, derived_role)
                                VALUES
                                    (:id, :userId, 'Missing Expiry', 'GOOGLE_TEMP',
                                     'UNCATEGORIZED', 'HEURISTIC', 'BACKLOG')
                                """,
                                Map.of("id", UUID.randomUUID(), "userId", USER_ID)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
