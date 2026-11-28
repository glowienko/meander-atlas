package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.PlaceCategoryStateView;
import com.meanderatlas.atlas.UpdatePlaceCategoryRequest;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdatePlaceCategory {

    private final AtlasPlaceRepository placeRepository;
    private final CurrentUserProvider currentUserProvider;

    public UpdatePlaceCategory(
            AtlasPlaceRepository placeRepository, CurrentUserProvider currentUserProvider) {
        this.placeRepository = placeRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public PlaceCategoryStateView execute(UUID rawPlaceId, UpdatePlaceCategoryRequest request) {
        Objects.requireNonNull(rawPlaceId, "placeId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        UserId userId = currentUserProvider.currentUserId();
        PlaceId placeId = new PlaceId(rawPlaceId);
        placeRepository.requireOwned(userId, placeId);

        String normalizedSubcategory = trimToNull(request.subcategory());
        placeRepository.updateManualCategory(
                userId, placeId, request.category(), normalizedSubcategory);

        return new PlaceCategoryStateView(
                placeId.value(), request.category(), normalizedSubcategory, CategorySource.MANUAL);
    }

    private static @Nullable String trimToNull(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
