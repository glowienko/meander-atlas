package com.meanderatlas.planning;

import com.meanderatlas.atlas.AtlasFacade;
import com.meanderatlas.atlas.AtlasHealthView;
import org.springframework.stereotype.Service;

@Service
public class PlanningFacade {

    private final AtlasFacade atlasFacade;

    public PlanningFacade(AtlasFacade atlasFacade) {
        this.atlasFacade = atlasFacade;
    }

    public AtlasHealthView atlasHealth() {
        return atlasFacade.health();
    }
}
