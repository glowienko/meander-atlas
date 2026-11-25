package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.AttachCsvMembershipCommand;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.atlas.domain.places.MatchedPlace;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.UserId;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttachCsvMembershipByCid {

    private final AtlasPlaceRepository placeRepository;
    private final CurrentUserProvider currentUserProvider;

    public AttachCsvMembershipByCid(
            AtlasPlaceRepository placeRepository, CurrentUserProvider currentUserProvider) {
        this.placeRepository = placeRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public boolean execute(AttachCsvMembershipCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UserId userId = currentUserProvider.currentUserId();
        Optional<MatchedPlace> matchedOpt =
                placeRepository.findByGoogleCid(userId, command.googleCid());
        if (matchedOpt.isEmpty()) {
            return false;
        }

        ImportedPlaceMerger.mergeIntoExisting(
                placeRepository,
                userId,
                matchedOpt.get(),
                command.category(),
                command.subcategory(),
                command.categorySource(),
                command.accountLabel(),
                command.listName(),
                command.listKind(),
                command.note(),
                command.tags(),
                command.sourceUrl(),
                command.savedAt(),
                null,
                null,
                null,
                command.initialVisitStatus(),
                command.initialVisitSource(),
                command.observedAt());
        return true;
    }
}
