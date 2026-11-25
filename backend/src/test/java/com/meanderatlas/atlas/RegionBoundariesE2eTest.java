package com.meanderatlas.atlas;

import static org.assertj.core.api.Assertions.assertThat;

import com.meanderatlas.TestEnvironment;
import com.meanderatlas.TestcontainersConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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

    @TempDir static Path boundariesDir;

    @LocalServerPort private int port;

    @Autowired private NamedParameterJdbcTemplate jdbc;

    @DynamicPropertySource
    static void registerBoundaryProperties(DynamicPropertyRegistry registry) {
        registry.add("app.boundaries-dir", () -> boundariesDir.toString());
        registry.add("app.boundaries-auto-seed", () -> "false");
    }

    @Test
    void seedsBoundariesAssignsPlacesAndServesClampedMvtTiles() throws IOException {
        writeSyntheticBoundaryFiles();
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID krakowId = UUID.randomUUID();
        UUID coastalId = UUID.randomUUID();
        insertUntaggedPlace(krakowId, userId, "Wawel Castle", 19.9354, 50.0540);
        insertUntaggedPlace(coastalId, userId, "Gdansk Pier", 18.67, 54.44);

        RestClient client =
                RestClient.builder().baseUrl(TestEnvironment.localhostUrl(port)).build();

        ResponseEntity<BoundarySeedResultView> seeded =
                client.post()
                        .uri("/api/v1/atlas/boundaries/seed")
                        .retrieve()
                        .toEntity(BoundarySeedResultView.class);

        assertThat(seeded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(seeded.getBody()).isNotNull();
        assertThat(seeded.getBody().countriesUpserted()).isEqualTo(2);
        assertThat(seeded.getBody().admin1Upserted()).isEqualTo(1);
        assertThat(seeded.getBody().placesTagged()).isEqualTo(2);
        assertThat(seeded.getBody().skipped()).isFalse();

        Map<String, @Nullable Object> krakowRow =
                jdbc.queryForMap(
                        "SELECT country_code, admin1_id FROM places WHERE id = :id",
                        Map.of("id", krakowId));
        assertThat(krakowRow.get("country_code")).isEqualTo("PL");
        assertThat(krakowRow.get("admin1_id")).isNotNull();

        Map<String, @Nullable Object> coastalRow =
                jdbc.queryForMap(
                        "SELECT country_code FROM places WHERE id = :id", Map.of("id", coastalId));
        assertThat(coastalRow.get("country_code")).isEqualTo("PL");

        ResponseEntity<BoundarySeedResultView> reseeded =
                client.post()
                        .uri("/api/v1/atlas/boundaries/seed")
                        .retrieve()
                        .toEntity(BoundarySeedResultView.class);
        assertThat(reseeded.getBody()).isNotNull();
        assertThat(reseeded.getBody().skipped()).isTrue();
        assertThat(reseeded.getBody().countriesUpserted()).isZero();

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

        ResponseEntity<byte[]> oceanTile =
                client.get()
                        .uri("/api/v1/atlas/tiles/regions/ADMIN1/4/0/8.mvt")
                        .retrieve()
                        .toEntity(byte[].class);
        assertThat(oceanTile.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        client.get()
                                .uri("/api/v1/atlas/tiles/regions/COUNTRY/1/2/0.mvt")
                                .retrieve()
                                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    private void insertUntaggedPlace(UUID id, UUID userId, String name, double lng, double lat) {
        jdbc.update(
                """
                INSERT INTO places
                    (id, user_id, canonical_name, geom, coord_source,
                     category, category_source, derived_role)
                VALUES
                    (:id, :userId, :name, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), 'TAKEOUT',
                     'LANDMARK', 'HEURISTIC', 'BACKLOG')
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("userId", userId)
                        .addValue("name", name)
                        .addValue("lng", lng)
                        .addValue("lat", lat));
    }

    private static void writeSyntheticBoundaryFiles() throws IOException {
        Files.writeString(
                boundariesDir.resolve("ne_10m_admin_0_countries.geojson"),
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
                      "properties": {"ISO_A2_EH": "AQ", "ADMIN": "Antarctica"},
                      "geometry": {
                        "type": "Polygon",
                        "coordinates": [[[-180.0, -89.9], [180.0, -89.9], [180.0, -70.0], [-180.0, -70.0], [-180.0, -89.9]]]
                      }
                    }
                  ]
                }
                """,
                StandardCharsets.UTF_8);
        Files.writeString(
                boundariesDir.resolve("ne_10m_admin_1_states_provinces.geojson"),
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
                    }
                  ]
                }
                """,
                StandardCharsets.UTF_8);
    }
}
