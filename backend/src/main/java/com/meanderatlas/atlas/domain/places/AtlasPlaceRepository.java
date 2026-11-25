package com.meanderatlas.atlas.domain.places;

import com.meanderatlas.atlas.domain.PlaceVisitState;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AtlasPlaceRepository {

    List<StoredPlace> findPlaces(UserId userId, PlaceQueryFilter filter);

    Optional<PlaceVisitState> findVisitState(UserId userId, PlaceId placeId);

    List<PlaceVisitState> findVisitStates(UserId userId, List<PlaceId> placeIds);

    boolean existsPlaceForUser(UserId userId, PlaceId placeId);

    Set<PlaceId> findOwnedPlaceIds(UserId userId, List<PlaceId> placeIds);

    void upsertVisitStates(List<PlaceVisitState> states);
}
