package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.PlaceEnrichmentTargetView;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.PlaceId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FindPlaceEnrichmentTargets {

    private final AtlasPlaceRepository placeRepository;
    private final CurrentUserProvider currentUserProvider;

    public FindPlaceEnrichmentTargets(
            AtlasPlaceRepository placeRepository, CurrentUserProvider currentUserProvider) {
        this.placeRepository = placeRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public List<PlaceEnrichmentTargetView> execute(List<PlaceId> placeIds) {
        if (placeIds.isEmpty()) {
            return List.of();
        }
        return placeRepository
                .findEnrichmentTargets(currentUserProvider.currentUserId(), placeIds)
                .stream()
                .map(target ->
                        new PlaceEnrichmentTargetView(
                                target.placeId(),
                                target.canonicalName(),
                                target.coordinates().latitude(),
                                target.coordinates().longitude(),
                                target.coordSource(),
                                target.category(),
                                target.categorySource(),
                                target.osmId(),
                                target.photoUrl(),
                                target.countryCode(),
                                target.cityName()))
                .toList();
    }
}
