package com.meanderatlas.atlas.adapter.persistence;

import com.meanderatlas.atlas.AtlasFacade;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.boundaries-auto-seed",
        havingValue = "true",
        matchIfMissing = true)
class BoundaryAutoSeeder implements ApplicationRunner {

    private final AtlasFacade atlasFacade;

    BoundaryAutoSeeder(AtlasFacade atlasFacade) {
        this.atlasFacade = atlasFacade;
    }

    @Override
    public void run(ApplicationArguments args) {
        atlasFacade.seedBoundaries(false);
    }
}
