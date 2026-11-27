package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.AttachCsvMembershipCommand;
import com.meanderatlas.atlas.application.ImportedPlaceMerger.ExistingPlaceMerge;
import com.meanderatlas.atlas.application.ImportedPlaceMerger.InitialVisitDetails;
import com.meanderatlas.atlas.application.ImportedPlaceMerger.MembershipDetails;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.shared.CoordSource;
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
        MembershipDetails membership =
                new MembershipDetails(
                        command.accountLabel(),
                        command.listName(),
                        command.listKind(),
                        command.note(),
                        command.tags(),
                        command.sourceUrl(),
                        command.savedAt());
        InitialVisitDetails initialVisit =
                InitialVisitDetails.ofNullable(
                        command.initialVisitStatus(),
                        command.initialVisitSource(),
                        command.observedAt());

        return placeRepository
                .findByGoogleCid(userId, command.googleCid())
                .map(matched -> {
                    boolean upgradeApproxCoordinates =
                            command.exactCoordinates() != null
                                    && matched.coordSource() == CoordSource.TAKEOUT_S2_APPROX;
                    placeMerger.mergeIntoExisting(
                            userId,
                            new ExistingPlaceMerge(
                                    matched,
                                    upgradeApproxCoordinates ? command.exactCoordinates() : null,
                                    upgradeApproxCoordinates ? CoordSource.TAKEOUT : null,
                                    command.category(),
                                    command.subcategory(),
                                    command.categorySource(),
                                    membership,
                                    null,
                                    null,
                                    null,
                                    initialVisit));
                    return matched.id();
                });
    }
}
