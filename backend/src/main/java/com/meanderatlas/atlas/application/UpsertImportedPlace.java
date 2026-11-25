package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.ImportedPlaceUpsertCommand;
import com.meanderatlas.atlas.UpsertedPlaceView;
import com.meanderatlas.atlas.domain.DerivedRoleResolver;
import com.meanderatlas.atlas.domain.RegionCodeNormalizer;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.atlas.domain.places.MatchedPlace;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
        String fallbackCountryCode =
                RegionCodeNormalizer.normalizeCountryCode(command.countryCode());

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
            ImportedPlaceMerger.applyInitialVisitIfPresent(
                    placeRepository,
                    userId,
                    placeId,
                    command.initialVisitStatus(),
                    command.initialVisitSource(),
                    command.observedAt(),
                    true);
            return new UpsertedPlaceView(placeId, true);
        }

        MatchedPlace matched = existing.get();
        String backfillPlaceId = matched.googlePlaceId() == null ? command.googlePlaceId() : null;
        Long backfillCid = matched.googleCid() == null ? command.googleCid() : null;

        ImportedPlaceMerger.mergeIntoExisting(
                placeRepository,
                userId,
                matched,
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
                backfillPlaceId,
                backfillCid,
                fallbackCountryCode,
                command.initialVisitStatus(),
                command.initialVisitSource(),
                command.observedAt());
        return new UpsertedPlaceView(matched.id(), false);
    }
}
