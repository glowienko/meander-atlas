package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.AtlasHealthView;
import com.meanderatlas.atlas.domain.AtlasHealthRepository;
import org.springframework.stereotype.Service;

@Service
public class GetAtlasHealth {

    private final AtlasHealthRepository atlasHealthRepository;

    public GetAtlasHealth(AtlasHealthRepository atlasHealthRepository) {
        this.atlasHealthRepository = atlasHealthRepository;
    }

    public AtlasHealthView execute() {
        String postgisVersion = atlasHealthRepository.fetchPostgisVersion();
        return AtlasHealthView.up(postgisVersion);
    }
}
