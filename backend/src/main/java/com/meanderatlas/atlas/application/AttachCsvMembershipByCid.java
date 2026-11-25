package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.AttachCsvMembershipCommand;
import com.meanderatlas.atlas.domain.DerivedRoleResolver;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.atlas.domain.places.MatchedPlace;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.UserId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
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

        MatchedPlace matched = matchedOpt.get();
        placeRepository.upsertMembership(
                userId,
                matched.id(),
                command.accountLabel(),
                command.listName(),
                command.listKind(),
                command.note(),
                command.tags(),
                command.sourceUrl(),
                null);

        List<ListKind> allKinds =
                Stream.concat(
                                placeRepository.findMembershipListKinds(matched.id()).stream(),
                                Stream.of(command.listKind()))
                        .distinct()
                        .toList();

        boolean canUpgradeCategory =
                matched.categorySource() != CategorySource.MANUAL
                        && matched.category() == PlaceCategory.UNCATEGORIZED
                        && command.category() != PlaceCategory.UNCATEGORIZED;
        PlaceCategory effectiveCategory =
                canUpgradeCategory ? command.category() : matched.category();
        DerivedRole resolvedRole = DerivedRoleResolver.resolve(effectiveCategory, allKinds);

        placeRepository.updatePlaceAfterMerge(
                userId,
                matched.id(),
                resolvedRole,
                canUpgradeCategory ? command.category() : null,
                canUpgradeCategory ? command.subcategory() : null,
                canUpgradeCategory ? command.categorySource() : null,
                null,
                null,
                null);
        return true;
    }
}
