package com.meanderatlas.atlas.adapter.web;

import com.meanderatlas.atlas.AtlasFacade;
import com.meanderatlas.atlas.AtlasHealthView;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AtlasHealthController.BASE_PATH)
class AtlasHealthController {

    static final String BASE_PATH = "/api/v1/atlas";
    static final String HEALTH_PATH = "/health";

    private final AtlasFacade atlasFacade;

    AtlasHealthController(AtlasFacade atlasFacade) {
        this.atlasFacade = atlasFacade;
    }

    @GetMapping(value = HEALTH_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    AtlasHealthView health() {
        return atlasFacade.health();
    }
}
