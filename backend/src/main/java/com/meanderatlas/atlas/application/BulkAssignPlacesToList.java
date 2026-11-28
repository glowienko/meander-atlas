package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.BulkAssignListRequest;
import com.meanderatlas.atlas.BulkAssignListResultView;
import com.meanderatlas.atlas.ListAssignMode;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BulkAssignPlacesToList {

    static final String DEFAULT_ACCOUNT_LABEL = "personal";

    private final AtlasPlaceRepository placeRepository;
    private final CurrentUserProvider currentUserProvider;

    public BulkAssignPlacesToList(
            AtlasPlaceRepository placeRepository, CurrentUserProvider currentUserProvider) {
        this.placeRepository = placeRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public BulkAssignListResultView execute(BulkAssignListRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String targetListName = requireNonBlank(request.targetListName(), "targetListName");
        ListKind targetKind = resolveEditableListKind(request.listKind());
        String accountLabel = resolveAccountLabel(request.accountLabel());
        String sourceListName = trimToNull(request.sourceListName());

        UserId userId = currentUserProvider.currentUserId();
        List<PlaceId> distinctPlaceIds =
                request.placeIds().stream().distinct().map(PlaceId::new).toList();
        placeRepository.requireAllOwned(userId, distinctPlaceIds);

        int removedCount =
                request.mode() == ListAssignMode.MOVE
                        ? placeRepository.deleteNonReviewMemberships(
                                userId,
                                distinctPlaceIds,
                                sourceListName,
                                accountLabel,
                                targetListName)
                        : 0;
        placeRepository.bulkUpsertMemberships(
                userId, distinctPlaceIds, accountLabel, targetListName, targetKind);
        placeRepository.recomputeDerivedRoles(userId, distinctPlaceIds);

        return new BulkAssignListResultView(
                distinctPlaceIds.size(), removedCount, accountLabel, targetListName, targetKind);
    }

    static ListKind resolveEditableListKind(@Nullable ListKind rawKind) {
        if (rawKind == null || rawKind == ListKind.REVIEW) {
            return ListKind.CUSTOM;
        }
        return rawKind;
    }

    static String resolveAccountLabel(@Nullable String rawAccountLabel) {
        String trimmed = trimToNull(rawAccountLabel);
        return trimmed == null ? DEFAULT_ACCOUNT_LABEL : trimmed;
    }

    static String requireNonBlank(@Nullable String raw, String field) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }

    static @Nullable String trimToNull(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
