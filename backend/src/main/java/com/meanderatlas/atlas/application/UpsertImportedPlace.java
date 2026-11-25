package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.ImportedPlaceUpsertCommand;
import com.meanderatlas.atlas.UpsertedPlaceView;
import com.meanderatlas.atlas.domain.DerivedRoleResolver;
import com.meanderatlas.atlas.domain.PlaceVisitState;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.atlas.domain.places.MatchedPlace;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpsertImportedPlace {

    private static final double DEDUPE_RADIUS_METERS = 50.0;
    private static final double MIN_TRIGRAM_SIMILARITY = 0.45;

    private final AtlasPlaceRepository placeRepository;
    private final CurrentUserProvider currentUserProvider;

    public UpsertImportedPlace(
            AtlasPlaceRepository placeRepository, CurrentUserProvider currentUserProvider) {
        this.placeRepository = placeRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public UpsertedPlaceView execute(ImportedPlaceUpsertCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UserId userId = currentUserProvider.currentUserId();
        String fallbackCountryCode = normalizeCountryCode(command.countryCode());

        Optional<MatchedPlace> existing =
                placeRepository.findCanonicalMatch(
                        userId,
                        command.googlePlaceId(),
                        command.googleCid(),
                        command.coordinates(),
                        command.canonicalName(),
                        DEDUPE_RADIUS_METERS,
                        MIN_TRIGRAM_SIMILARITY);

        if (existing.isEmpty()) {
            DerivedRole initialRole =
                    DerivedRoleResolver.resolve(command.category(), List.of(command.listKind()));
            PlaceId placeId =
                    placeRepository.insertImportedPlace(
                            userId,
                            command.canonicalName(),
                            command.coordinates(),
                            command.coordSource(),
                            command.googlePlaceId(),
                            command.googleCid(),
                            command.category(),
                            command.subcategory(),
                            command.categorySource(),
                            initialRole,
                            fallbackCountryCode,
                            command.cityName());
            placeRepository.upsertMembership(
                    userId,
                    placeId,
                    command.accountLabel(),
                    command.listName(),
                    command.listKind(),
                    command.note(),
                    command.tags(),
                    command.sourceUrl(),
                    command.savedAt());
            PlaceVisitState initialState = PlaceVisitState.unvisited(placeId, userId);
            PlaceVisitState appliedState = applyInitialVisit(initialState, command);
            placeRepository.upsertVisitStates(List.of(appliedState));
            return new UpsertedPlaceView(placeId, true);
        }

        MatchedPlace matched = existing.get();
        placeRepository.upsertMembership(
                userId,
                matched.id(),
                command.accountLabel(),
                command.listName(),
                command.listKind(),
                command.note(),
                command.tags(),
                command.sourceUrl(),
                command.savedAt());

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

        String backfillPlaceId = matched.googlePlaceId() == null ? command.googlePlaceId() : null;
        Long backfillCid = matched.googleCid() == null ? command.googleCid() : null;

        placeRepository.updatePlaceAfterMerge(
                userId,
                matched.id(),
                resolvedRole,
                canUpgradeCategory ? command.category() : null,
                canUpgradeCategory ? command.subcategory() : null,
                canUpgradeCategory ? command.categorySource() : null,
                backfillPlaceId,
                backfillCid,
                fallbackCountryCode);

        if (command.initialVisitStatus() != null && command.initialVisitSource() != null) {
            PlaceVisitState current =
                    placeRepository
                            .findVisitState(userId, matched.id())
                            .orElseGet(() -> PlaceVisitState.unvisited(matched.id(), userId));
            PlaceVisitState next = applyInitialVisit(current, command);
            if (!next.equals(current)) {
                placeRepository.upsertVisitStates(List.of(next));
            }
        }
        return new UpsertedPlaceView(matched.id(), false);
    }

    private static PlaceVisitState applyInitialVisit(
            PlaceVisitState current, ImportedPlaceUpsertCommand command) {
        VisitStatus status = command.initialVisitStatus();
        VisitSource source = command.initialVisitSource();
        if (status == null || source == null) {
            return current;
        }
        if (source == VisitSource.REVIEW
                && current.status() == VisitStatus.VISITED
                && current.source() == VisitSource.REVIEW) {
            return current;
        }
        return current.apply(status, source, command.observedAt());
    }

    private static @Nullable String normalizeCountryCode(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
