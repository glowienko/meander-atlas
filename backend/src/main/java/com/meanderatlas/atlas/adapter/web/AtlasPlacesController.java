package com.meanderatlas.atlas.adapter.web;

import com.meanderatlas.atlas.AtlasFacade;
import com.meanderatlas.atlas.AtlasPlaceFilterQuery;
import com.meanderatlas.atlas.AtlasPlacesResponse;
import com.meanderatlas.atlas.BulkVisitStatusRequest;
import com.meanderatlas.atlas.BulkVisitStatusResultView;
import com.meanderatlas.atlas.PlaceCategoryStateView;
import com.meanderatlas.atlas.PlaceListsStateView;
import com.meanderatlas.atlas.PlaceVisitStateView;
import com.meanderatlas.atlas.RestoreVisitStatesRequest;
import com.meanderatlas.atlas.RestoreVisitStatesResultView;
import com.meanderatlas.atlas.UpdatePlaceCategoryRequest;
import com.meanderatlas.atlas.UpdatePlaceListsRequest;
import com.meanderatlas.atlas.UpdatePlaceVisitStatusRequest;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.VisitStatus;
import jakarta.validation.Valid;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/atlas/places")
class AtlasPlacesController {

    private final AtlasFacade atlasFacade;

    AtlasPlacesController(AtlasFacade atlasFacade) {
        this.atlasFacade = atlasFacade;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    AtlasPlacesResponse places(
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(required = false) @Nullable VisitStatus visitStatus,
            @RequestParam(required = false) @Nullable DerivedRole derivedRole,
            @RequestParam(required = false) @Nullable PlaceCategory category,
            @RequestParam(required = false) @Nullable String account,
            @RequestParam(required = false) @Nullable String list,
            @RequestParam(required = false) @Nullable String countryCode,
            @RequestParam(required = false) @Nullable UUID admin1Id,
            @RequestParam(required = false) @Nullable Double minLng,
            @RequestParam(required = false) @Nullable Double minLat,
            @RequestParam(required = false) @Nullable Double maxLng,
            @RequestParam(required = false) @Nullable Double maxLat) {
        return atlasFacade.places(
                new AtlasPlaceFilterQuery(
                        q,
                        visitStatus,
                        derivedRole,
                        category,
                        account,
                        list,
                        countryCode,
                        admin1Id,
                        minLng,
                        minLat,
                        maxLng,
                        maxLat));
    }

    @PatchMapping(
            value = "/{id}/visit-status",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    PlaceVisitStateView updateVisitStatus(
            @PathVariable UUID id, @Valid @RequestBody UpdatePlaceVisitStatusRequest request) {
        return atlasFacade.updatePlaceVisitStatus(id, request);
    }

    @PatchMapping(
            value = "/{id}/category",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    PlaceCategoryStateView updateCategory(
            @PathVariable UUID id, @Valid @RequestBody UpdatePlaceCategoryRequest request) {
        return atlasFacade.updatePlaceCategory(id, request);
    }

    @PutMapping(
            value = "/{id}/lists",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    PlaceListsStateView updateLists(
            @PathVariable UUID id, @Valid @RequestBody UpdatePlaceListsRequest request) {
        return atlasFacade.updatePlaceLists(id, request);
    }

    @PatchMapping(
            value = "/bulk-visit-status",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    BulkVisitStatusResultView bulkUpdateVisitStatus(
            @Valid @RequestBody BulkVisitStatusRequest request) {
        return atlasFacade.bulkUpdatePlaceVisitStatus(request);
    }

    @PostMapping(
            value = "/bulk-visit-status/restore",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    RestoreVisitStatesResultView restoreVisitStates(
            @Valid @RequestBody RestoreVisitStatesRequest request) {
        return atlasFacade.restorePlaceVisitStates(request);
    }
}
