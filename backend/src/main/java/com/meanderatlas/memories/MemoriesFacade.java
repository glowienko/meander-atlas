package com.meanderatlas.memories;

import com.meanderatlas.atlas.AtlasFacade;
import com.meanderatlas.atlas.AtlasHealthView;
import org.springframework.stereotype.Service;

@Service
public class MemoriesFacade {

    private final AtlasFacade atlasFacade;

    public MemoriesFacade(AtlasFacade atlasFacade) {
        this.atlasFacade = atlasFacade;
    }

    public AtlasHealthView atlasHealth() {
        return atlasFacade.health();
    }
}
