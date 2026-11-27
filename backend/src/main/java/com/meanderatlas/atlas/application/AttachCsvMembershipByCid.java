package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.AttachCsvMembershipCommand;
import com.meanderatlas.atlas.application.ImportedPlaceMerger.ExistingPlaceMerge;
import com.meanderatlas.atlas.application.ImportedPlaceMerger.InitialVisitDetails;
import com.meanderatlas.atlas.application.ImportedPlaceMerger.MembershipDetails;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttachCsvMembershipByCid {

    private final AtlasPlaceRepository placeRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ImportedPlaceMerger placeMerger;

    public AttachCsvMembershipByCid(
            AtlasPlaceRepository placeRepository,
            CurrentUserProvider currentUserProvider,
            ImportedPlaceMerger placeMerger) {
        this.placeRepository = placeRepository;
        this.currentUserProvider = currentUserProvider;
        this.placeMerger = placeMerger;
    }

    @Transactional
    public Optional<PlaceId> execute(AttachCsvMembershipCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UserId userId = currentUserProvider.currentUserId();
        MembershipDetails membership = MembershipDetails.from(command);
        InitialVisitDetails initialVisit = InitialVisitDetails.from(command);

        return placeRepository
                .findByGoogleCid(userId, command.googleCid())
                .map(matched -> {
                    placeMerger.mergeIntoExisting(
                            userId,
                            ExistingPlaceMerge.forCsvAttach(
                                    matched, command, membership, initialVisit));
                    return matched.id();
                });
    }
}
