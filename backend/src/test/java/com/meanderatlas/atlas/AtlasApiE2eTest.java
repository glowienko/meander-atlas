package com.meanderatlas.atlas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meanderatlas.TestEnvironment;
import com.meanderatlas.TestcontainersConfiguration;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Tag("e2e")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AtlasApiE2eTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final List<String> TEST_REGION_CODES =
            List.of("PL-API", "JP-API", "PL-12-API", "JP-26-API");

    @LocalServerPort private int port;

    @Autowired private NamedParameterJdbcTemplate jdbc;

    @AfterEach
    void cleanupTestRows() {
        jdbc.update("DELETE FROM places WHERE user_id = :userId", Map.of("userId", USER_ID));
        jdbc.update(
                "DELETE FROM region_boundaries WHERE code IN (:codes)",
                Map.of("codes", TEST_REGION_CODES));
    }

    @Test
    void queriesPlacesRegionsStatsAndSupportsBulkVisitStatusWithUndo() {
        SeededDataset ids = seedAtlasDataset();
        RestClient client =
                RestClient.builder().baseUrl(TestEnvironment.localhostUrl(port)).build();

        verifyPlaceFiltering(client, ids);
        verifyRegionsAndDossiers(client);
        verifyStats(client);
        verifyAntimeridianBbox(client);
        verifyVisitStatusAndBulkUndo(client, ids);
        verifyErrorResponses(client, ids);
    }

    private SeededDataset seedAtlasDataset() {
        jdbc.update("DELETE FROM places WHERE user_id = :userId", Map.of("userId", USER_ID));

        UUID plCountryId = UUID.randomUUID();
        UUID jpCountryId = UUID.randomUUID();
        UUID plAdmin1Id = UUID.randomUUID();
        UUID jpAdmin1Id = UUID.randomUUID();

        insertBoundary(
                plCountryId,
                "COUNTRY",
                "PL-API",
                "PL-API",
                "Poland",
                "MULTIPOLYGON(((19.0 49.0, 21.0 49.0, 21.0 51.0, 19.0 51.0, 19.0 49.0)))");
        insertBoundary(
                jpCountryId,
                "COUNTRY",
                "JP-API",
                "JP-API",
                "Japan",
                "MULTIPOLYGON(((135.0 34.0, 136.0 34.0, 136.0 35.5, 135.0 35.5, 135.0 34.0)))");
        insertBoundary(
                plAdmin1Id,
                "ADMIN1",
                "PL-12-API",
                "PL-API",
                "Malopolskie",
                "MULTIPOLYGON(((19.5 49.8, 20.5 49.8, 20.5 50.3, 19.5 50.3, 19.5 49.8)))");
        insertBoundary(
                jpAdmin1Id,
                "ADMIN1",
                "JP-26-API",
                "JP-API",
                "Kyoto",
                "MULTIPOLYGON(((135.5 34.8, 135.9 34.8, 135.9 35.2, 135.5 35.2, 135.5 34.8)))");

        UUID wawelId = UUID.randomUUID();
        UUID halaId = UUID.randomUUID();
        UUID fushimiId = UUID.randomUUID();
        UUID hotelId = UUID.randomUUID();

        insertPlace(
                wawelId,
                "Wawel Royal Castle",
                19.9354,
                50.0540,
                "LANDMARK",
                "castle",
                "MUST_SEE",
                "PL-API",
                plAdmin1Id,
                "Krakow");
        insertPlace(
                halaId,
                "Hala Targowa",
                19.9470,
                50.0580,
                "RESTAURANT",
                "market",
                "FAVORITE",
                "PL-API",
                plAdmin1Id,
                "Krakow");
        insertPlace(
                fushimiId,
                "Fushimi Inari Taisha",
                135.7727,
                34.9671,
                "SACRAL",
                "shrine",
                "BACKLOG",
                "JP-API",
                jpAdmin1Id,
                "Kyoto");
        insertPlace(
                hotelId,
                "Kyoto Garden Hotel",
                135.7600,
                35.0100,
                "HOTEL",
                "hotel",
                "PREFERRED_HOTEL",
                "JP-API",
                jpAdmin1Id,
                "Kyoto");
        jdbc.update(
                "UPDATE places SET photo_url = :photoUrl WHERE id = :id",
                Map.of(
                        "id",
                        hotelId,
                        "photoUrl",
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a9/Kyoto.jpg/640px-Kyoto.jpg"));

        Instant wawelVisitAt = Instant.parse("2026-08-10T09:00:00Z");
        Instant triageAt = Instant.parse("2026-08-15T11:00:00Z");
        insertVisitState(wawelId, "VISITED", "TIMELINE", 2, wawelVisitAt, wawelVisitAt);
        insertVisitState(fushimiId, "NEEDS_TRIAGE", "TIMELINE", 0, null, triageAt);

        insertMembership(wawelId, "personal", "Starred", "STARRED", "Golden landmark");
        insertMembership(halaId, "personal", "Ulubione", "FAVORITE", "Great pierogi");
        insertMembership(fushimiId, "secondary", "Want to go", "WANT_TO_GO", "Red torii");
        insertMembership(hotelId, "personal", "Starred", "STARRED", "Basecamp stay");

        return new SeededDataset(wawelId, halaId, fushimiId, hotelId, plAdmin1Id);
    }

    private static void verifyPlaceFiltering(RestClient client, SeededDataset ids) {
        AtlasPlacesResponse allPlaces =
                client.get().uri("/api/v1/atlas/places").retrieve().body(AtlasPlacesResponse.class);
        assertThat(allPlaces).isNotNull();
        assertThat(allPlaces.totalCount()).isEqualTo(4);
        AtlasPlaceView wawelView = findById(allPlaces.places(), ids.wawelId());
        assertThat(wawelView.coordSource()).isEqualTo(CoordSource.TAKEOUT);
        assertThat(wawelView.photoUrl()).isNull();
        AtlasPlaceView hotelView = findById(allPlaces.places(), ids.hotelId());
        assertThat(hotelView.coordSource()).isEqualTo(CoordSource.TAKEOUT);
        assertThat(hotelView.photoUrl())
                .isEqualTo(
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a9/Kyoto.jpg/640px-Kyoto.jpg");

        AtlasPlacesResponse filteredByNoteAndAccount =
                client.get()
                        .uri(
                                "/api/v1/atlas/places?q=pierogi&account=personal&list=Ulubione"
                                        + "&visitStatus=UNVISITED&derivedRole=FAVORITE&category=RESTAURANT")
                        .retrieve()
                        .body(AtlasPlacesResponse.class);
        assertThat(filteredByNoteAndAccount).isNotNull();
        assertThat(filteredByNoteAndAccount.places())
                .extracting(AtlasPlaceView::canonicalName)
                .containsExactly("Hala Targowa");

        AtlasPlacesResponse krakowBbox =
                client.get()
                        .uri("/api/v1/atlas/places?minLng=19.8&minLat=50.0&maxLng=20.0&maxLat=50.1")
                        .retrieve()
                        .body(AtlasPlacesResponse.class);
        assertThat(krakowBbox).isNotNull();
        assertThat(krakowBbox.totalCount()).isEqualTo(2);

        AtlasPlacesResponse byAdmin1Id =
                client.get()
                        .uri("/api/v1/atlas/places?admin1Id={admin1Id}", ids.plAdmin1Id())
                        .retrieve()
                        .body(AtlasPlacesResponse.class);
        assertThat(byAdmin1Id).isNotNull();
        assertThat(byAdmin1Id.places())
                .extracting(AtlasPlaceView::canonicalName)
                .containsExactlyInAnyOrder("Wawel Royal Castle", "Hala Targowa");

        AtlasPlacesResponse literalWildcardPercent =
                client.get()
                        .uri("/api/v1/atlas/places?q={q}", "%")
                        .retrieve()
                        .body(AtlasPlacesResponse.class);
        assertThat(literalWildcardPercent).isNotNull();
        assertThat(literalWildcardPercent.totalCount()).isZero();

        AtlasPlacesResponse literalWildcardUnderscore =
                client.get()
                        .uri("/api/v1/atlas/places?q={q}", "_")
                        .retrieve()
                        .body(AtlasPlacesResponse.class);
        assertThat(literalWildcardUnderscore).isNotNull();
        assertThat(literalWildcardUnderscore.totalCount()).isZero();

        AtlasPlacesResponse visitedOnly =
                client.get()
                        .uri("/api/v1/atlas/places?visitStatus=VISITED")
                        .retrieve()
                        .body(AtlasPlacesResponse.class);
        assertThat(visitedOnly).isNotNull();
        assertThat(visitedOnly.places())
                .extracting(AtlasPlaceView::canonicalName)
                .containsExactly("Wawel Royal Castle");

        AtlasPlacesResponse triageOnly =
                client.get()
                        .uri("/api/v1/atlas/places?visitStatus=NEEDS_TRIAGE")
                        .retrieve()
                        .body(AtlasPlacesResponse.class);
        assertThat(triageOnly).isNotNull();
        assertThat(triageOnly.places())
                .extracting(AtlasPlaceView::canonicalName)
                .containsExactly("Fushimi Inari Taisha");
    }

    private static void verifyRegionsAndDossiers(RestClient client) {
        List<RegionSummaryView> countries =
                client.get()
                        .uri("/api/v1/atlas/regions?level=COUNTRY")
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        assertThat(countries).isNotNull().hasSize(2);

        List<RegionSummaryView> filteredCountries =
                client.get()
                        .uri(
                                "/api/v1/atlas/regions?level=COUNTRY&category=RESTAURANT&derivedRole=FAVORITE")
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        assertThat(filteredCountries).isNotNull().hasSize(1);
        assertThat(filteredCountries.getFirst().code()).isEqualTo("PL-API");
        assertThat(filteredCountries.getFirst().totalPlaces()).isEqualTo(1);

        List<RegionSummaryView> plAdmin1Regions =
                client.get()
                        .uri("/api/v1/atlas/regions?level=ADMIN1&countryCode= pl-api ")
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        assertThat(plAdmin1Regions).isNotNull().hasSize(1);
        assertThat(plAdmin1Regions.getFirst().code()).isEqualTo("PL-12-API");

        List<RegionSummaryView> sacralAdmin1Regions =
                client.get()
                        .uri("/api/v1/atlas/regions?level=ADMIN1&category=SACRAL")
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        assertThat(sacralAdmin1Regions).isNotNull().hasSize(1);
        assertThat(sacralAdmin1Regions.getFirst().code()).isEqualTo("JP-26-API");

        RegionDossierView polandDossier =
                client.get()
                        .uri("/api/v1/atlas/regions/COUNTRY/PL-API")
                        .retrieve()
                        .body(RegionDossierView.class);
        assertThat(polandDossier).isNotNull();
        assertThat(polandDossier.region().totalPlaces()).isEqualTo(2);
        assertThat(polandDossier.region().visitedPlaces()).isEqualTo(1);
        assertThat(polandDossier.region().completionRatio()).isEqualTo(0.5);
        assertThat(polandDossier.childRegions()).hasSize(1);
        assertThat(polandDossier.places()).hasSize(2);

        RegionDossierView malopolskieDossier =
                client.get()
                        .uri("/api/v1/atlas/regions/ADMIN1/PL-12-API")
                        .retrieve()
                        .body(RegionDossierView.class);
        assertThat(malopolskieDossier).isNotNull();
        assertThat(malopolskieDossier.region().name()).isEqualTo("Malopolskie");
        assertThat(malopolskieDossier.places()).hasSize(2);
    }

    private static void verifyStats(RestClient client) {
        AtlasStatsView stats =
                client.get().uri("/api/v1/atlas/stats").retrieve().body(AtlasStatsView.class);
        assertThat(stats).isNotNull();
        assertThat(stats.totalMappedPlaces()).isEqualTo(4);
        assertThat(stats.visitedPlaces()).isEqualTo(1);
        assertThat(stats.unvisitedPlaces()).isEqualTo(2);
        assertThat(stats.needsTriagePlaces()).isEqualTo(1);
        assertThat(stats.visitedCountries()).isEqualTo(1);
        assertThat(stats.totalSavedCountries()).isEqualTo(2);
        assertThat(stats.visitedAdmin1Regions()).isEqualTo(1);
        assertThat(stats.totalSavedAdmin1Regions()).isEqualTo(2);
        assertThat(stats.accountLists()).hasSize(3);
    }

    private void verifyAntimeridianBbox(RestClient client) {
        UUID fijiId = UUID.randomUUID();
        UUID samoaId = UUID.randomUUID();
        insertPlaceWithoutRegion(fijiId, "Fiji Reef", 179.5, -18.0);
        insertPlaceWithoutRegion(samoaId, "Samoa Bay", -175.0, -14.0);
        try {
            AtlasPlacesResponse pacific =
                    client.get()
                            .uri(
                                    "/api/v1/atlas/places?minLng=170.0&minLat=-25.0&maxLng=-170.0&maxLat=0.0")
                            .retrieve()
                            .body(AtlasPlacesResponse.class);
            assertThat(pacific).isNotNull();
            assertThat(pacific.places())
                    .extracting(AtlasPlaceView::canonicalName)
                    .containsExactlyInAnyOrder("Fiji Reef", "Samoa Bay");
        } finally {
            jdbc.update(
                    "DELETE FROM places WHERE id IN (:ids)",
                    Map.of("ids", List.of(fijiId, samoaId)));
        }
    }

    private static void verifyVisitStatusAndBulkUndo(RestClient client, SeededDataset ids) {
        PlaceVisitStateView hotelUpdated =
                client.patch()
                        .uri("/api/v1/atlas/places/{id}/visit-status", ids.hotelId())
                        .body(new UpdatePlaceVisitStatusRequest(VisitStatus.VISITED))
                        .retrieve()
                        .body(PlaceVisitStateView.class);
        assertThat(hotelUpdated).isNotNull();
        assertThat(hotelUpdated.status()).isEqualTo(VisitStatus.VISITED);
        assertThat(hotelUpdated.source()).isEqualTo(VisitSource.MANUAL_SINGLE);
        assertThat(hotelUpdated.previousState().status()).isEqualTo(VisitStatus.UNVISITED);

        BulkVisitStatusResultView bulkResult =
                client.patch()
                        .uri("/api/v1/atlas/places/bulk-visit-status")
                        .body(
                                new BulkVisitStatusRequest(
                                        List.of(ids.wawelId(), ids.halaId(), ids.fushimiId()),
                                        VisitStatus.VISITED))
                        .retrieve()
                        .body(BulkVisitStatusResultView.class);
        assertThat(bulkResult).isNotNull();
        assertThat(bulkResult.updatedCount()).isEqualTo(3);
        assertThat(bulkResult.previousStates()).hasSize(3);

        ResponseEntity<RestoreVisitStatesResultView> restored =
                client.post()
                        .uri("/api/v1/atlas/places/bulk-visit-status/restore")
                        .body(new RestoreVisitStatesRequest(bulkResult.previousStates()))
                        .retrieve()
                        .toEntity(RestoreVisitStatesResultView.class);
        assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restored.getBody()).isNotNull();
        assertThat(restored.getBody().restoredCount()).isEqualTo(3);

        AtlasPlacesResponse afterUndo =
                client.get()
                        .uri("/api/v1/atlas/places?countryCode=PL-API")
                        .retrieve()
                        .body(AtlasPlacesResponse.class);
        assertThat(afterUndo).isNotNull();
        AtlasPlaceView halaAfterUndo = findById(afterUndo.places(), ids.halaId());
        assertThat(halaAfterUndo.visitStatus()).isEqualTo(VisitStatus.UNVISITED);
        assertThat(halaAfterUndo.visitSource()).isNull();
        assertThat(halaAfterUndo.derivedRole()).isEqualTo(DerivedRole.FAVORITE);

        AtlasPlaceView wawelAfterUndo = findById(afterUndo.places(), ids.wawelId());
        assertThat(wawelAfterUndo.visitStatus()).isEqualTo(VisitStatus.VISITED);
        assertThat(wawelAfterUndo.visitSource()).isEqualTo(VisitSource.TIMELINE);
        assertThat(wawelAfterUndo.visitCount()).isEqualTo(2);

        BulkVisitStatusResultView noOpTriage =
                client.patch()
                        .uri("/api/v1/atlas/places/bulk-visit-status")
                        .body(
                                new BulkVisitStatusRequest(
                                        List.of(ids.wawelId()), VisitStatus.NEEDS_TRIAGE))
                        .retrieve()
                        .body(BulkVisitStatusResultView.class);
        assertThat(noOpTriage).isNotNull();
        assertThat(noOpTriage.updatedCount()).isZero();
        assertThat(noOpTriage.previousStates()).isEmpty();
    }

    private static void verifyErrorResponses(RestClient client, SeededDataset ids) {
        UUID unknownId = UUID.randomUUID();
        assertThatThrownBy(() ->
                        client.get()
                                .uri("/api/v1/atlas/regions/COUNTRY/UNKNOWN")
                                .retrieve()
                                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class);

        assertThatThrownBy(() ->
                        client.get()
                                .uri("/api/v1/atlas/places?minLng=19.0")
                                .retrieve()
                                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class);

        assertThatThrownBy(() ->
                        client.patch()
                                .uri("/api/v1/atlas/places/{id}/visit-status", unknownId)
                                .body(new UpdatePlaceVisitStatusRequest(VisitStatus.VISITED))
                                .retrieve()
                                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class);

        assertThatThrownBy(() ->
                        client.patch()
                                .uri("/api/v1/atlas/places/bulk-visit-status")
                                .body(
                                        new BulkVisitStatusRequest(
                                                List.of(unknownId), VisitStatus.VISITED))
                                .retrieve()
                                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class);

        assertThatThrownBy(() ->
                        client.post()
                                .uri("/api/v1/atlas/places/bulk-visit-status/restore")
                                .body(
                                        new RestoreVisitStatesRequest(
                                                List.of(
                                                        new VisitStateSnapshotView(
                                                                unknownId,
                                                                VisitStatus.UNVISITED,
                                                                null,
                                                                0,
                                                                null,
                                                                null))))
                                .retrieve()
                                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class);

        assertThatThrownBy(() ->
                        client.patch()
                                .uri("/api/v1/atlas/places/bulk-visit-status")
                                .body(new BulkVisitStatusRequest(List.of(), VisitStatus.VISITED))
                                .retrieve()
                                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class);

        VisitStateSnapshotView dupSnapshot =
                new VisitStateSnapshotView(
                        ids.halaId(), VisitStatus.UNVISITED, null, 0, null, null);
        assertThatThrownBy(() ->
                        client.post()
                                .uri("/api/v1/atlas/places/bulk-visit-status/restore")
                                .body(
                                        new RestoreVisitStatesRequest(
                                                List.of(dupSnapshot, dupSnapshot)))
                                .retrieve()
                                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class);

        VisitStateSnapshotView invalidVisitedSnapshot =
                new VisitStateSnapshotView(
                        ids.halaId(),
                        VisitStatus.VISITED,
                        VisitSource.MANUAL_SINGLE,
                        0,
                        null,
                        null);
        assertThatThrownBy(() ->
                        client.post()
                                .uri("/api/v1/atlas/places/bulk-visit-status/restore")
                                .body(
                                        new RestoreVisitStatesRequest(
                                                List.of(invalidVisitedSnapshot)))
                                .retrieve()
                                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    private static AtlasPlaceView findById(List<AtlasPlaceView> places, UUID id) {
        return places.stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    private void insertBoundary(
            UUID id, String level, String code, String countryCode, String name, String wkt) {
        jdbc.update(
                """
                INSERT INTO region_boundaries
                    (id, region_level, code, country_code, name, geom, simplified_geom)
                VALUES
                    (:id, :level, :code, :countryCode, :name,
                     ST_GeomFromText(:wkt, 4326), ST_GeomFromText(:wkt, 4326))
                """,
                Map.of(
                        "id", id,
                        "level", level,
                        "code", code,
                        "countryCode", countryCode,
                        "name", name,
                        "wkt", wkt));
    }

    private void insertPlace(
            UUID id,
            String name,
            double lng,
            double lat,
            String category,
            String subcategory,
            String derivedRole,
            String countryCode,
            UUID admin1Id,
            String cityName) {
        jdbc.update(
                """
                INSERT INTO places
                    (id, user_id, canonical_name, geom, coord_source, category, subcategory,
                     category_source, derived_role, country_code, admin1_id, city_name)
                VALUES
                    (:id, :userId, :name, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), 'TAKEOUT',
                     :category, :subcategory, 'HEURISTIC', :derivedRole, :countryCode, :admin1Id, :cityName)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("userId", USER_ID)
                        .addValue("name", name)
                        .addValue("lng", lng)
                        .addValue("lat", lat)
                        .addValue("category", category)
                        .addValue("subcategory", subcategory)
                        .addValue("derivedRole", derivedRole)
                        .addValue("countryCode", countryCode)
                        .addValue("admin1Id", admin1Id)
                        .addValue("cityName", cityName));
    }

    private void insertVisitState(
            UUID placeId,
            String status,
            String source,
            int visitCount,
            @Nullable Instant firstVisitedAt,
            @Nullable Instant lastVisitedAt) {
        jdbc.update(
                """
                INSERT INTO place_visit_state
                    (place_id, user_id, status, source, visit_count, first_visited_at, last_visited_at)
                VALUES
                    (:placeId, :userId, :status, :source, :visitCount, :firstVisitedAt, :lastVisitedAt)
                """,
                new MapSqlParameterSource()
                        .addValue("placeId", placeId)
                        .addValue("userId", USER_ID)
                        .addValue("status", status)
                        .addValue("source", source)
                        .addValue("visitCount", visitCount)
                        .addValue(
                                "firstVisitedAt",
                                firstVisitedAt == null
                                        ? null
                                        : OffsetDateTime.ofInstant(firstVisitedAt, ZoneOffset.UTC),
                                Types.TIMESTAMP_WITH_TIMEZONE)
                        .addValue(
                                "lastVisitedAt",
                                lastVisitedAt == null
                                        ? null
                                        : OffsetDateTime.ofInstant(lastVisitedAt, ZoneOffset.UTC),
                                Types.TIMESTAMP_WITH_TIMEZONE));
    }

    private void insertPlaceWithoutRegion(UUID id, String name, double lng, double lat) {
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
                        .addValue("userId", USER_ID)
                        .addValue("name", name)
                        .addValue("lng", lng)
                        .addValue("lat", lat));
    }

    private void insertMembership(
            UUID placeId, String account, String listName, String listKind, String note) {
        jdbc.update(
                """
                INSERT INTO place_memberships
                    (id, place_id, user_id, account_label, list_name, list_kind, note)
                VALUES
                    (:id, :placeId, :userId, :account, :listName, :listKind, :note)
                """,
                Map.of(
                        "id", UUID.randomUUID(),
                        "placeId", placeId,
                        "userId", USER_ID,
                        "account", account,
                        "listName", listName,
                        "listKind", listKind,
                        "note", note));
    }

    private record SeededDataset(
            UUID wawelId, UUID halaId, UUID fushimiId, UUID hotelId, UUID plAdmin1Id) {}
}
