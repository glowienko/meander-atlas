package com.meanderatlas.atlas.adapter.web;

import com.meanderatlas.atlas.AtlasFacade;
import com.meanderatlas.atlas.BulkAssignListRequest;
import com.meanderatlas.atlas.BulkAssignListResultView;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/atlas/lists")
class AtlasListsController {

    private final AtlasFacade atlasFacade;

    AtlasListsController(AtlasFacade atlasFacade) {
        this.atlasFacade = atlasFacade;
    }

    @PostMapping(
            value = "/bulk-assign",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    BulkAssignListResultView bulkAssign(@Valid @RequestBody BulkAssignListRequest request) {
        return atlasFacade.bulkAssignPlacesToList(request);
    }
}
