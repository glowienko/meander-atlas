package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.PlaceId;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FindPlacesNeedingEnrichment {

    private final AtlasPlaceRepository placeRepository;
    private final CurrentUserProvider currentUserProvider;

    public FindPlacesNeedingEnrichment(
            AtlasPlaceRepository placeRepository, CurrentUserProvider currentUserProvider) {
        this.placeRepository = placeRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public List<PlaceId> execute(@Nullable List<PlaceId> within) {
        return placeRepository.findPlaceIdsNeedingEnrichment(
                currentUserProvider.currentUserId(), within);
    }
}
