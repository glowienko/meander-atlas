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

final class ImportedPlaceMerger {

    private ImportedPlaceMerger() {}

    static void mergeIntoExisting(
            AtlasPlaceRepository placeRepository,
            UserId userId,
            MatchedPlace matched,
            PlaceCategory incomingCategory,
            @Nullable String incomingSubcategory,
            CategorySource incomingCategorySource,
            String accountLabel,
            String listName,
            ListKind listKind,
            @Nullable String note,
            @Nullable String tags,
            @Nullable String sourceUrl,
            @Nullable Instant savedAt,
            @Nullable String googlePlaceIdToBackfill,
            @Nullable Long googleCidToBackfill,
            @Nullable String fallbackCountryCode,
            @Nullable VisitStatus initialVisitStatus,
            @Nullable VisitSource initialVisitSource,
            @Nullable Instant observedAt) {
        placeRepository.upsertMembership(
                userId,
                matched.id(),
                accountLabel,
                listName,
                listKind,
                note,
                tags,
                sourceUrl,
                savedAt);

        List<ListKind> allKinds = placeRepository.findMembershipListKinds(matched.id());

        boolean canUpgradeCategory =
                matched.categorySource() != CategorySource.MANUAL
                        && matched.category() == PlaceCategory.UNCATEGORIZED
                        && incomingCategory != PlaceCategory.UNCATEGORIZED;
        PlaceCategory effectiveCategory =
                canUpgradeCategory ? incomingCategory : matched.category();
        DerivedRole resolvedRole = DerivedRoleResolver.resolve(effectiveCategory, allKinds);

        placeRepository.updatePlaceAfterMerge(
                userId,
                matched.id(),
                resolvedRole,
                canUpgradeCategory ? incomingCategory : null,
                canUpgradeCategory ? incomingSubcategory : null,
                canUpgradeCategory ? incomingCategorySource : null,
                googlePlaceIdToBackfill,
                googleCidToBackfill,
                fallbackCountryCode);

        applyInitialVisitIfPresent(
                placeRepository,
                userId,
                matched.id(),
                initialVisitStatus,
                initialVisitSource,
                observedAt,
                false);
    }

    static void applyInitialVisitIfPresent(
            AtlasPlaceRepository placeRepository,
            UserId userId,
            PlaceId placeId,
            @Nullable VisitStatus status,
            @Nullable VisitSource source,
            @Nullable Instant observedAt,
            boolean newlyCreated) {
        if (status == null || source == null) {
            if (newlyCreated) {
                placeRepository.upsertVisitStates(
                        List.of(PlaceVisitState.unvisited(placeId, userId)));
            }
            return;
        }
        PlaceVisitState current =
                newlyCreated
                        ? PlaceVisitState.unvisited(placeId, userId)
                        : placeRepository
                                .findVisitState(userId, placeId)
                                .orElseGet(() -> PlaceVisitState.unvisited(placeId, userId));
        PlaceVisitState next = applyInitialVisit(current, status, source, observedAt);
        if (newlyCreated || !next.equals(current)) {
            placeRepository.upsertVisitStates(List.of(next));
        }
    }

    private static PlaceVisitState applyInitialVisit(
            PlaceVisitState current,
            VisitStatus status,
            VisitSource source,
            @Nullable Instant observedAt) {
        if (source == VisitSource.REVIEW
                && current.status() == VisitStatus.VISITED
                && current.source() == VisitSource.REVIEW) {
            return current;
        }
        return current.apply(status, source, observedAt);
    }
}
