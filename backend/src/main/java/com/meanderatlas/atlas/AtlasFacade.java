package com.meanderatlas.atlas;

import com.meanderatlas.atlas.application.GetAtlasHealth;
import org.springframework.stereotype.Service;

@Service
public class AtlasFacade {

    private final GetAtlasHealth getAtlasHealth;

    public AtlasFacade(GetAtlasHealth getAtlasHealth) {
        this.getAtlasHealth = getAtlasHealth;
    }

    public AtlasHealthView health() {
        return getAtlasHealth.execute();
    }
}
