package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.AtlasPlaceFilterQuery;
import com.meanderatlas.atlas.AtlasPlaceView;
import com.meanderatlas.atlas.AtlasPlacesResponse;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.atlas.domain.places.PlaceQueryFilter;
import com.meanderatlas.shared.CurrentUserProvider;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueryAtlasPlaces {

    private final AtlasPlaceRepository placeRepository;
    private final CurrentUserProvider currentUserProvider;

    public QueryAtlasPlaces(
            AtlasPlaceRepository placeRepository, CurrentUserProvider currentUserProvider) {
        this.placeRepository = placeRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public AtlasPlacesResponse execute(AtlasPlaceFilterQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        PlaceQueryFilter filter = AtlasViewMapper.toPlaceQueryFilter(query);
        List<AtlasPlaceView> places =
                placeRepository.findPlaces(currentUserProvider.currentUserId(), filter).stream()
                        .map(AtlasViewMapper::toPlaceView)
                        .toList();
        return new AtlasPlacesResponse(places, places.size());
    }
}
