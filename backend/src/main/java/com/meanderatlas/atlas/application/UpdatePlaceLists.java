package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.PlaceListEntryRequest;
import com.meanderatlas.atlas.PlaceListsStateView;
import com.meanderatlas.atlas.PlaceMembershipView;
import com.meanderatlas.atlas.UpdatePlaceListsRequest;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.atlas.domain.places.PlaceListMembershipTarget;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdatePlaceLists {

    private final AtlasPlaceRepository placeRepository;
    private final CurrentUserProvider currentUserProvider;

    public UpdatePlaceLists(
            AtlasPlaceRepository placeRepository, CurrentUserProvider currentUserProvider) {
        this.placeRepository = placeRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public PlaceListsStateView execute(UUID rawPlaceId, UpdatePlaceListsRequest request) {
        Objects.requireNonNull(rawPlaceId, "placeId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        UserId userId = currentUserProvider.currentUserId();
        PlaceId placeId = new PlaceId(rawPlaceId);
        placeRepository.requireOwned(userId, placeId);

        List<PlaceListMembershipTarget> deduplicatedTargets =
                request.lists().stream()
                        .map(UpdatePlaceLists::toTarget)
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        target ->
                                                target.accountLabel()
                                                        + "\u0000"
                                                        + target.listName(),
                                        Function.identity(),
                                        (first, _) -> first))
                        .values()
                        .stream()
                        .toList();

        placeRepository.replaceNonReviewMemberships(userId, placeId, deduplicatedTargets);
        DerivedRole derivedRole = placeRepository.recomputeDerivedRole(userId, placeId);
        List<PlaceMembershipView> memberships =
                placeRepository.findMembershipsForPlace(userId, placeId).stream()
                        .map(AtlasViewMapper::toMembershipView)
                        .toList();

        return new PlaceListsStateView(placeId.value(), derivedRole, memberships);
    }

    private static PlaceListMembershipTarget toTarget(PlaceListEntryRequest entry) {
        return new PlaceListMembershipTarget(
                BulkAssignPlacesToList.resolveAccountLabel(entry.accountLabel()),
                BulkAssignPlacesToList.requireNonBlank(entry.listName(), "listName"),
                BulkAssignPlacesToList.resolveEditableListKind(entry.listKind()));
    }
}
