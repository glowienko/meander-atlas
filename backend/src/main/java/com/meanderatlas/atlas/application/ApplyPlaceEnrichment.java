package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.PlaceEnrichmentCommand;
import com.meanderatlas.atlas.PlaceEnrichmentResultView;
import com.meanderatlas.atlas.domain.DerivedRoleResolver;
import com.meanderatlas.atlas.domain.RegionCodeNormalizer;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.atlas.domain.places.PlaceEnrichmentTarget;
import com.meanderatlas.atlas.domain.places.PlaceEnrichmentUpdate;
import com.meanderatlas.atlas.domain.places.PlaceNameGuard;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.UserId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplyPlaceEnrichment {

    private final AtlasPlaceRepository placeRepository;
    private final CurrentUserProvider currentUserProvider;

    public ApplyPlaceEnrichment(
            AtlasPlaceRepository placeRepository, CurrentUserProvider currentUserProvider) {
        this.placeRepository = placeRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public PlaceEnrichmentResultView execute(PlaceEnrichmentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UserId userId = currentUserProvider.currentUserId();
        PlaceEnrichmentTarget current =
                placeRepository.findEnrichmentTargets(userId, List.of(command.placeId())).stream()
                        .findFirst()
                        .orElseThrow(() ->
                                new NoSuchElementException(
                                        "Place not found: " + command.placeId().value()));

        boolean upgradeCoords =
                command.refinedCoordinates() != null
                        && current.coordSource() == CoordSource.TAKEOUT_S2_APPROX;
        PlaceCategory candidateCategory = command.category();
        boolean upgradeCategory =
                candidateCategory != null
                        && candidateCategory != PlaceCategory.UNCATEGORIZED
                        && current.categorySource() != CategorySource.MANUAL
                        && (current.categorySource() != CategorySource.OPEN_CATALOG
                                || current.category() != candidateCategory);
        PlaceCategory effectiveCategory =
                upgradeCategory ? Objects.requireNonNull(candidateCategory) : current.category();
        List<ListKind> listKinds = placeRepository.findMembershipListKinds(command.placeId());
        DerivedRole nextDerivedRole = DerivedRoleResolver.resolve(effectiveCategory, listKinds);

        String photoUrl = RegionCodeNormalizer.trimToNull(command.photoUrl());
        String candidateName = RegionCodeNormalizer.trimToNull(command.resolvedCanonicalName());
        String upgradedCanonicalName =
                candidateName != null
                                && PlaceNameGuard.isCoordinateOnlyName(current.canonicalName())
                                && !PlaceNameGuard.isCoordinateOnlyName(candidateName)
                        ? candidateName
                        : null;
        boolean photoAdded =
                photoUrl != null && (current.photoUrl() == null || current.photoUrl().isBlank());

        placeRepository.applyPlaceEnrichment(
                userId,
                new PlaceEnrichmentUpdate(
                        command.placeId(),
                        upgradedCanonicalName,
                        upgradeCoords ? command.refinedCoordinates() : null,
                        RegionCodeNormalizer.trimToNull(command.osmId()),
                        upgradeCategory ? effectiveCategory : null,
                        upgradeCategory
                                ? RegionCodeNormalizer.trimToNull(command.subcategory())
                                : null,
                        nextDerivedRole,
                        RegionCodeNormalizer.trimToNull(command.cityName()),
                        photoUrl,
                        RegionCodeNormalizer.trimToNull(command.openingHoursJson())));

        return new PlaceEnrichmentResultView(
                command.placeId(), upgradeCoords, upgradeCategory, photoAdded);
    }
}
