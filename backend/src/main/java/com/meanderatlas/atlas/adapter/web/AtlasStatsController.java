package com.meanderatlas.atlas.adapter.web;

import com.meanderatlas.atlas.AtlasFacade;
import com.meanderatlas.atlas.AtlasStatsView;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/atlas/stats")
class AtlasStatsController {

    private final AtlasFacade atlasFacade;

    AtlasStatsController(AtlasFacade atlasFacade) {
        this.atlasFacade = atlasFacade;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    AtlasStatsView stats() {
        return atlasFacade.stats();
    }
}
