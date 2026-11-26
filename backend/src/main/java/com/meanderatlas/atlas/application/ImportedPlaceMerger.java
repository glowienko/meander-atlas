package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.domain.DerivedRoleResolver;
import com.meanderatlas.atlas.domain.PlaceVisitState;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.atlas.domain.places.MatchedPlace;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class ImportedPlaceMerger {

    private final AtlasPlaceRepository placeRepository;

    ImportedPlaceMerger(AtlasPlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    void initializeVisitState(
            UserId userId, PlaceId placeId, @Nullable InitialVisitDetails initialVisit) {
        PlaceVisitState base = PlaceVisitState.unvisited(placeId, userId);
        PlaceVisitState initial =
                initialVisit == null
                        ? base
                        : base.apply(
                                initialVisit.status(),
                                initialVisit.source(),
                                initialVisit.observedAt());
        placeRepository.upsertVisitStates(List.of(initial));
    }

    void mergeIntoExisting(UserId userId, ExistingPlaceMerge merge) {
        MatchedPlace matched = merge.matched();
        MembershipDetails membership = merge.membership();
        placeRepository.upsertMembership(
                userId,
                matched.id(),
                membership.accountLabel(),
                membership.listName(),
                membership.listKind(),
                membership.note(),
                membership.tags(),
                membership.sourceUrl(),
                membership.savedAt());

        List<ListKind> allKinds = placeRepository.findMembershipListKinds(matched.id());

        boolean canUpgradeCategory =
                matched.categorySource() != CategorySource.MANUAL
                        && matched.category() == PlaceCategory.UNCATEGORIZED
                        && merge.incomingCategory() != PlaceCategory.UNCATEGORIZED;
        PlaceCategory effectiveCategory =
                canUpgradeCategory ? merge.incomingCategory() : matched.category();
        DerivedRole resolvedRole = DerivedRoleResolver.resolve(effectiveCategory, allKinds);

        placeRepository.updatePlaceAfterMerge(
                userId,
                matched.id(),
                resolvedRole,
                canUpgradeCategory ? merge.incomingCategory() : null,
                canUpgradeCategory ? merge.incomingSubcategory() : null,
                canUpgradeCategory ? merge.incomingCategorySource() : null,
                merge.googlePlaceIdToBackfill(),
                merge.googleCidToBackfill(),
                merge.fallbackCountryCode());

        applyInitialVisitToExisting(userId, matched.id(), merge.initialVisit());
    }

    private void applyInitialVisitToExisting(
            UserId userId, PlaceId placeId, @Nullable InitialVisitDetails initialVisit) {
        if (initialVisit == null) {
            return;
        }
        PlaceVisitState current =
                placeRepository
                        .findVisitState(userId, placeId)
                        .orElseGet(() -> PlaceVisitState.unvisited(placeId, userId));
        PlaceVisitState next = applyInitialVisit(current, initialVisit);
        if (!next.equals(current)) {
            placeRepository.upsertVisitStates(List.of(next));
        }
    }

    private static PlaceVisitState applyInitialVisit(
            PlaceVisitState current, InitialVisitDetails initialVisit) {
        if (initialVisit.source() == VisitSource.REVIEW
                && current.status() == VisitStatus.VISITED
                && current.source() == VisitSource.REVIEW) {
            return current;
        }
        return current.apply(
                initialVisit.status(), initialVisit.source(), initialVisit.observedAt());
    }

    record MembershipDetails(
            String accountLabel,
            String listName,
            ListKind listKind,
            @Nullable String note,
            @Nullable String tags,
            @Nullable String sourceUrl,
            @Nullable Instant savedAt) {}

    record InitialVisitDetails(
            VisitStatus status, VisitSource source, @Nullable Instant observedAt) {

        static @Nullable InitialVisitDetails ofNullable(
                @Nullable VisitStatus status,
                @Nullable VisitSource source,
                @Nullable Instant observedAt) {
            if (status == null || source == null) {
                return null;
            }
            return new InitialVisitDetails(status, source, observedAt);
        }
    }

    record ExistingPlaceMerge(
            MatchedPlace matched,
            PlaceCategory incomingCategory,
            @Nullable String incomingSubcategory,
            CategorySource incomingCategorySource,
            MembershipDetails membership,
            @Nullable String googlePlaceIdToBackfill,
            @Nullable Long googleCidToBackfill,
            @Nullable String fallbackCountryCode,
            @Nullable InitialVisitDetails initialVisit) {}
}
