package com.meanderatlas.atlas.adapter.web;

import com.meanderatlas.atlas.AtlasFacade;
import com.meanderatlas.atlas.AtlasHealthView;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/atlas")
class AtlasHealthController {

    private final AtlasFacade atlasFacade;

    AtlasHealthController(AtlasFacade atlasFacade) {
        this.atlasFacade = atlasFacade;
    }

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    AtlasHealthView health() {
        return atlasFacade.health();
    }
}
