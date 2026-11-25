package com.meanderatlas.atlas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meanderatlas.TestEnvironment;
import com.meanderatlas.TestcontainersConfiguration;
import com.meanderatlas.atlas.domain.boundaries.BoundaryTestData;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Types;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Tag("e2e")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegionBoundariesE2eTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @TempDir static Path boundariesDir;

    @LocalServerPort private int port;

    @Autowired private NamedParameterJdbcTemplate jdbc;

    @DynamicPropertySource
    static void registerBoundaryProperties(DynamicPropertyRegistry registry) {
        registry.add("app.boundaries-dir", () -> boundariesDir.toString());
        registry.add("app.boundaries-auto-seed", () -> "false");
    }

    @BeforeEach
    @AfterEach
    void cleanupState() {
        jdbc.update("DELETE FROM places WHERE user_id = :userId", Map.of("userId", USER_ID));
        jdbc.update("DELETE FROM region_boundaries", Map.of());
    }

    @Test
    void seedsBoundariesAssignsPlacesPreservesFkAndServesBufferedMvtTiles() throws IOException {
        writeE2eBoundaryFiles();

        UUID krakowId = UUID.randomUUID();
        UUID coastalId = UUID.randomUUID();
        UUID offshoreId = UUID.randomUUID();
        UUID corsicaCoastalId = UUID.randomUUID();

        insertPlace(krakowId, "Wawel Castle", 19.9354, 50.0540, null, null);
        insertPlace(coastalId, "Gdansk Pier", 18.67, 54.44, null, null);
        insertPlace(offshoreId, "Baltic Deep Buoy", 18.0, 56.5, null, null);
        insertPlace(corsicaCoastalId, "Corsica Coast Pin", 9.63, 42.5, null, null);

        RestClient client =
                RestClient.builder().baseUrl(TestEnvironment.localhostUrl(port)).build();

        ResponseEntity<BoundarySeedResultView> seeded =
                client.post()
                        .uri("/api/v1/atlas/boundaries/seed")
                        .retrieve()
                        .toEntity(BoundarySeedResultView.class);

        assertThat(seeded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(seeded.getBody()).isNotNull();
        assertThat(seeded.getBody().countriesUpserted()).isEqualTo(4);
        assertThat(seeded.getBody().admin1Upserted()).isEqualTo(4);
        assertThat(seeded.getBody().placesTagged()).isEqualTo(3);
        assertThat(seeded.getBody().skipped()).isFalse();

        Map<String, @Nullable Object> krakowRow =
                jdbc.queryForMap(
                        "SELECT country_code, admin1_id FROM places WHERE id = :id",
                        Map.of("id", krakowId));
        assertThat(krakowRow.get("country_code")).isEqualTo("PL");
        UUID krakowAdmin1Id = (UUID) krakowRow.get("admin1_id");
        assertThat(krakowAdmin1Id).isNotNull();

        Map<String, @Nullable Object> coastalRow =
                jdbc.queryForMap(
                        "SELECT country_code FROM places WHERE id = :id", Map.of("id", coastalId));
        assertThat(coastalRow.get("country_code")).isEqualTo("PL");

        Map<String, @Nullable Object> offshoreRow =
                jdbc.queryForMap(
                        "SELECT country_code, admin1_id FROM places WHERE id = :id",
                        Map.of("id", offshoreId));
        assertThat(offshoreRow.get("country_code")).isNull();
        assertThat(offshoreRow.get("admin1_id")).isNull();

        Map<String, @Nullable Object> corsicaPinRow =
                jdbc.queryForMap(
                        "SELECT p.country_code, rb.code AS admin1_code, rb.country_code AS admin1_country "
                                + "FROM places p LEFT JOIN region_boundaries rb ON rb.id = p.admin1_id "
                                + "WHERE p.id = :id",
                        Map.of("id", corsicaCoastalId));
        assertThat(corsicaPinRow.get("country_code")).isEqualTo("FR");
        assertThat(corsicaPinRow.get("admin1_code")).isEqualTo("FRA-1001");
        assertThat(corsicaPinRow.get("admin1_country")).isEqualTo("FR");

        UUID tuscanyId =
                jdbc.queryForObject(
                        "SELECT id FROM region_boundaries WHERE region_level = 'ADMIN1' AND code = 'IT-52'",
                        Map.of(),
                        UUID.class);
        UUID pretaggedId = UUID.randomUUID();
        insertPlace(pretaggedId, "Pretagged Custom Pin", 19.94, 50.06, "ZZ", tuscanyId);

        ResponseEntity<BoundarySeedResultView> reseeded =
                client.post()
                        .uri("/api/v1/atlas/boundaries/seed")
                        .retrieve()
                        .toEntity(BoundarySeedResultView.class);
        assertThat(reseeded.getBody()).isNotNull();
        assertThat(reseeded.getBody().skipped()).isTrue();
        assertThat(reseeded.getBody().countriesUpserted()).isZero();

        ResponseEntity<BoundarySeedResultView> forcedSeed =
                client.post()
                        .uri("/api/v1/atlas/boundaries/seed?force=true")
                        .retrieve()
                        .toEntity(BoundarySeedResultView.class);
        assertThat(forcedSeed.getBody()).isNotNull();
        assertThat(forcedSeed.getBody().skipped()).isFalse();
        assertThat(forcedSeed.getBody().countriesUpserted()).isEqualTo(4);
        assertThat(forcedSeed.getBody().placesTagged()).isZero();

        UUID krakowAdmin1AfterForce =
                jdbc.queryForObject(
                        "SELECT admin1_id FROM places WHERE id = :id",
                        Map.of("id", krakowId),
                        UUID.class);
        assertThat(krakowAdmin1AfterForce).isEqualTo(krakowAdmin1Id);

        Map<String, @Nullable Object> pretaggedRow =
                jdbc.queryForMap(
                        "SELECT country_code, admin1_id FROM places WHERE id = :id",
                        Map.of("id", pretaggedId));
        assertThat(pretaggedRow.get("country_code")).isEqualTo("ZZ");
        assertThat(pretaggedRow.get("admin1_id")).isEqualTo(tuscanyId);

        ResponseEntity<byte[]> worldTile =
                client.get()
                        .uri("/api/v1/atlas/tiles/regions/COUNTRY/0/0/0.mvt")
                        .retrieve()
                        .toEntity(byte[].class);
        assertThat(worldTile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(worldTile.getHeaders().getContentType())
                .hasToString("application/vnd.mapbox-vector-tile");
        assertThat(worldTile.getBody()).isNotEmpty();

        ResponseEntity<byte[]> krakowAdmin1Tile =
                client.get()
                        .uri("/api/v1/atlas/tiles/regions/ADMIN1/4/8/5.mvt?countryCode=pl")
                        .retrieve()
                        .toEntity(byte[].class);
        assertThat(krakowAdmin1Tile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(krakowAdmin1Tile.getBody()).isNotEmpty();

        ResponseEntity<byte[]> franceAdmin1Tile =
                client.get()
                        .uri("/api/v1/atlas/tiles/regions/ADMIN1/4/8/5.mvt?countryCode=FR")
                        .retrieve()
                        .toEntity(byte[].class);
        assertThat(franceAdmin1Tile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(franceAdmin1Tile.getBody()).isNotEmpty();

        ResponseEntity<byte[]> fraCodeTile =
                client.get()
                        .uri("/api/v1/atlas/tiles/regions/ADMIN1/4/8/5.mvt?countryCode=FRA")
                        .retrieve()
                        .toEntity(byte[].class);
        assertThat(fraCodeTile.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<byte[]> bufferedEdgeTile =
                client.get()
                        .uri("/api/v1/atlas/tiles/regions/ADMIN1/4/8/5.mvt?countryCode=BF")
                        .retrieve()
                        .toEntity(byte[].class);
        assertThat(bufferedEdgeTile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bufferedEdgeTile.getBody()).isNotEmpty();

        ResponseEntity<byte[]> unmatchedCountryTile =
                client.get()
                        .uri("/api/v1/atlas/tiles/regions/ADMIN1/4/8/5.mvt?countryCode=XX")
                        .retrieve()
                        .toEntity(byte[].class);
        assertThat(unmatchedCountryTile.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<byte[]> oceanTile =
                client.get()
                        .uri("/api/v1/atlas/tiles/regions/ADMIN1/4/0/8.mvt")
                        .retrieve()
                        .toEntity(byte[].class);
        assertThat(oceanTile.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThatThrownBy(() ->
                        client.get()
                                .uri("/api/v1/atlas/tiles/regions/COUNTRY/1/2/0.mvt")
                                .retrieve()
                                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    private static void writeE2eBoundaryFiles() throws IOException {
        BoundaryTestData.writeGeoJson(
                boundariesDir,
                "ne_10m_admin_0_countries.geojson",
                """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "properties": {"ISO_A2_EH": "PL", "ADMIN": "Poland"},
                      "geometry": {
                        "type": "MultiPolygon",
                        "coordinates": [[[[18.0, 49.0], [22.0, 49.0], [22.0, 54.4], [18.0, 54.4], [18.0, 49.0]]]]
                      }
                    },
                    {
                      "type": "Feature",
                      "properties": {"ISO_A2": "-99", "ISO_A2_EH": "FR", "ADM0_A3": "FRA", "ADMIN": "France"},
                      "geometry": {
                        "type": "Polygon",
                        "coordinates": [[[8.5, 41.3], [9.6, 41.3], [9.6, 43.0], [8.5, 43.0], [8.5, 41.3]]]
                      }
                    },
                    {
                      "type": "Feature",
                      "properties": {"ISO_A2_EH": "IT", "ADMIN": "Italy"},
                      "geometry": {
                        "type": "Polygon",
                        "coordinates": [[[9.70, 41.3], [11.0, 41.3], [11.0, 44.0], [9.70, 44.0], [9.70, 41.3]]]
                      }
                    },
                    {
                      "type": "Feature",
                      "properties": {"ISO_A2_EH": "AQ", "ADMIN": "Antarctica"},
                      "geometry": {
                        "type": "Polygon",
                        "coordinates": [[[-180.0, -89.9], [180.0, -89.9], [180.0, -70.0], [-180.0, -70.0], [-180.0, -89.9]]]
                      }
                    }
                  ]
                }
                """);
        BoundaryTestData.writeGeoJson(
                boundariesDir,
                "ne_10m_admin_1_states_provinces.geojson",
                """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "properties": {"iso_3166_2": "PL-12", "iso_a2": "PL", "name": "Małopolskie"},
                      "geometry": {
                        "type": "Polygon",
                        "coordinates": [[[19.0, 49.4], [21.2, 49.4], [21.2, 50.6], [19.0, 50.6], [19.0, 49.4]]]
                      }
                    },
                    {
                      "type": "Feature",
                      "properties": {
                        "iso_3166_2": "FR-X~",
                        "adm1_code": "FRA-1001",
                        "iso_a2": "-99",
                        "adm0_a3": "FRA",
                        "name": "Corsica"
                      },
                      "geometry": {
                        "type": "Polygon",
                        "coordinates": [[[8.5, 41.3], [9.6, 41.3], [9.6, 43.0], [8.5, 43.0], [8.5, 41.3]]]
                      }
                    },
                    {
                      "type": "Feature",
                      "properties": {"iso_3166_2": "IT-52", "iso_a2": "IT", "name": "Tuscany"},
                      "geometry": {
                        "type": "Polygon",
                        "coordinates": [[[9.64, 41.3], [11.0, 41.3], [11.0, 44.0], [9.64, 44.0], [9.64, 41.3]]]
                      }
                    },
                    {
                      "type": "Feature",
                      "properties": {"iso_3166_2": "BF-01", "iso_a2": "BF", "name": "Edge Buffer Region"},
                      "geometry": {
                        "type": "Polygon",
                        "coordinates": [[[22.6, 50.0], [22.9, 50.0], [22.9, 50.5], [22.6, 50.5], [22.6, 50.0]]]
                      }
                    }
                  ]
                }
                """);
    }

    private void insertPlace(
            UUID id,
            String name,
            double lng,
            double lat,
            @Nullable String countryCode,
            @Nullable UUID admin1Id) {
        jdbc.update(
                """
                INSERT INTO places
                    (id, user_id, canonical_name, geom, coord_source,
                     category, category_source, derived_role, country_code, admin1_id)
                VALUES
                    (:id, :userId, :name, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), 'TAKEOUT',
                     'LANDMARK', 'HEURISTIC', 'BACKLOG', :countryCode, :admin1Id)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("userId", USER_ID)
                        .addValue("name", name)
                        .addValue("lng", lng)
                        .addValue("lat", lat)
                        .addValue("countryCode", countryCode, Types.VARCHAR)
                        .addValue("admin1Id", admin1Id, Types.OTHER));
    }
}
