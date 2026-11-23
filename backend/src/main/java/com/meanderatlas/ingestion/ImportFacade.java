package com.meanderatlas.ingestion;

import com.meanderatlas.atlas.AtlasFacade;
import com.meanderatlas.atlas.AtlasHealthView;
import org.springframework.stereotype.Service;

@Service
public class ImportFacade {

    private final AtlasFacade atlasFacade;

    public ImportFacade(AtlasFacade atlasFacade) {
        this.atlasFacade = atlasFacade;
    }

    public AtlasHealthView atlasHealth() {
        return atlasFacade.health();
    }
}
