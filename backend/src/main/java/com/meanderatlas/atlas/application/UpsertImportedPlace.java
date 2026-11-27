package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.ImportedPlaceUpsertCommand;
import com.meanderatlas.atlas.UpsertedPlaceView;
import com.meanderatlas.atlas.application.ImportedPlaceMerger.ExistingPlaceMerge;
import com.meanderatlas.atlas.application.ImportedPlaceMerger.InitialVisitDetails;
import com.meanderatlas.atlas.application.ImportedPlaceMerger.MembershipDetails;
import com.meanderatlas.atlas.domain.DerivedRoleResolver;
import com.meanderatlas.atlas.domain.RegionCodeNormalizer;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.atlas.domain.places.MatchedPlace;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpsertImportedPlace {

    private static final double DEDUPE_RADIUS_METERS = 50.0;
    private static final double MIN_TRIGRAM_SIMILARITY = 0.45;

    private final AtlasPlaceRepository placeRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ImportedPlaceMerger placeMerger;

    public UpsertImportedPlace(
            AtlasPlaceRepository placeRepository,
            CurrentUserProvider currentUserProvider,
            ImportedPlaceMerger placeMerger) {
        this.placeRepository = placeRepository;
        this.currentUserProvider = currentUserProvider;
        this.placeMerger = placeMerger;
    }

    @Transactional
    public UpsertedPlaceView execute(ImportedPlaceUpsertCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UserId userId = currentUserProvider.currentUserId();
        String fallbackCountryCode =
                RegionCodeNormalizer.normalizeCountryCode(command.countryCode());
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
                .findCanonicalMatch(
                        userId,
                        command.googlePlaceId(),
                        command.googleCid(),
                        command.coordinates(),
                        command.canonicalName(),
                        DEDUPE_RADIUS_METERS,
                        MIN_TRIGRAM_SIMILARITY)
                .map(matched ->
                        mergeExisting(
                                userId,
                                matched,
                                command,
                                membership,
                                fallbackCountryCode,
                                initialVisit))
                .orElseGet(() ->
                        insertNew(userId, command, membership, fallbackCountryCode, initialVisit));
    }

    private UpsertedPlaceView insertNew(
            UserId userId,
            ImportedPlaceUpsertCommand command,
            MembershipDetails membership,
            @Nullable String fallbackCountryCode,
            @Nullable InitialVisitDetails initialVisit) {
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
                membership.accountLabel(),
                membership.listName(),
                membership.listKind(),
                membership.note(),
                membership.tags(),
                membership.sourceUrl(),
                membership.savedAt());
        placeMerger.initializeVisitState(userId, placeId, initialVisit);
        return new UpsertedPlaceView(placeId, true);
    }

    private UpsertedPlaceView mergeExisting(
            UserId userId,
            MatchedPlace matched,
            ImportedPlaceUpsertCommand command,
            MembershipDetails membership,
            @Nullable String fallbackCountryCode,
            @Nullable InitialVisitDetails initialVisit) {
        String backfillPlaceId = matched.googlePlaceId() == null ? command.googlePlaceId() : null;
        Long backfillCid = matched.googleCid() == null ? command.googleCid() : null;
        boolean upgradeApproxCoordinates =
                matched.coordSource() == CoordSource.TAKEOUT_S2_APPROX
                        && command.coordSource() == CoordSource.TAKEOUT;
        placeMerger.mergeIntoExisting(
                userId,
                new ExistingPlaceMerge(
                        matched,
                        upgradeApproxCoordinates ? command.coordinates() : null,
                        upgradeApproxCoordinates ? command.coordSource() : null,
                        command.category(),
                        command.subcategory(),
                        command.categorySource(),
                        membership,
                        backfillPlaceId,
                        backfillCid,
                        fallbackCountryCode,
                        initialVisit));
        return new UpsertedPlaceView(matched.id(), false);
    }
}
