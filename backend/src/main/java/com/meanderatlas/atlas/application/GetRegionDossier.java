package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.AtlasPlaceView;
import com.meanderatlas.atlas.RegionDossierView;
import com.meanderatlas.atlas.RegionScope;
import com.meanderatlas.atlas.RegionSummaryView;
import com.meanderatlas.atlas.domain.RegionCodeNormalizer;
import com.meanderatlas.atlas.domain.RegionLevel;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.atlas.domain.places.PlaceQueryFilter;
import com.meanderatlas.atlas.domain.stats.AtlasStatsRepository;
import com.meanderatlas.atlas.domain.stats.RegionSummary;
import com.meanderatlas.atlas.domain.stats.RegionSummaryFilter;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.UserId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetRegionDossier {

    private final AtlasStatsRepository statsRepository;
    private final AtlasPlaceRepository placeRepository;
    private final CurrentUserProvider currentUserProvider;

    public GetRegionDossier(
            AtlasStatsRepository statsRepository,
            AtlasPlaceRepository placeRepository,
            CurrentUserProvider currentUserProvider) {
        this.statsRepository = statsRepository;
        this.placeRepository = placeRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public RegionDossierView execute(RegionScope scope, String code) {
        Objects.requireNonNull(scope, "scope must not be null");
        String normalizedCode =
                RegionCodeNormalizer.normalizeCountryCode(
                        Objects.requireNonNull(code, "code must not be null"));
        if (normalizedCode == null) {
            throw new IllegalArgumentException("Region code must not be blank");
        }
        UserId userId = currentUserProvider.currentUserId();
        RegionLevel level = AtlasViewMapper.toRegionLevel(scope);
        RegionSummary summary =
                statsRepository
                        .findRegionSummary(userId, level, normalizedCode)
                        .orElseThrow(() ->
                                new NoSuchElementException(
                                        "Region not found: " + level + " " + normalizedCode));

        List<RegionSummaryView> childRegions =
                scope == RegionScope.COUNTRY
                        ? statsRepository
                                .findRegionSummaries(
                                        userId,
                                        RegionLevel.ADMIN1,
                                        summary.countryCode(),
                                        RegionSummaryFilter.empty())
                                .stream()
                                .map(AtlasViewMapper::toRegionSummaryView)
                                .toList()
                        : List.of();
        PlaceQueryFilter placeFilter =
                scope == RegionScope.COUNTRY
                        ? PlaceQueryFilter.forCountry(summary.countryCode())
                        : PlaceQueryFilter.forAdmin1(summary.id());
        List<AtlasPlaceView> places =
                placeRepository.findPlaces(userId, placeFilter).stream()
                        .map(AtlasViewMapper::toPlaceView)
                        .toList();
        return new RegionDossierView(
                AtlasViewMapper.toRegionSummaryView(summary), childRegions, places);
    }
}
