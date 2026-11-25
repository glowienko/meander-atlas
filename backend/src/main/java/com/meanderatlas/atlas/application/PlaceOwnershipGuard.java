package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

final class PlaceOwnershipGuard {

    private PlaceOwnershipGuard() {}

    static void ensureOwned(
            AtlasPlaceRepository placeRepository, UserId userId, List<PlaceId> placeIds) {
        Set<PlaceId> ownedIds = placeRepository.findOwnedPlaceIds(userId, placeIds);
        if (ownedIds.size() != placeIds.size()) {
            throw new NoSuchElementException("One or more places were not found");
        }
    }
}
