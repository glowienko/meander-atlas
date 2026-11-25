package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.AtlasPlaceFilterQuery;
import com.meanderatlas.atlas.RegionScope;
import com.meanderatlas.atlas.RegionSummaryView;
import com.meanderatlas.atlas.domain.places.PlaceQueryFilter;
import com.meanderatlas.atlas.domain.stats.AtlasStatsRepository;
import com.meanderatlas.shared.CurrentUserProvider;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class GetRegionSummaries {

    private final AtlasStatsRepository statsRepository;
    private final CurrentUserProvider currentUserProvider;

    public GetRegionSummaries(
            AtlasStatsRepository statsRepository, CurrentUserProvider currentUserProvider) {
        this.statsRepository = statsRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public List<RegionSummaryView> execute(
            RegionScope scope, @Nullable String countryCode, AtlasPlaceFilterQuery query) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(query, "query must not be null");
        PlaceQueryFilter filter = AtlasViewMapper.toPlaceQueryFilter(query);
        return statsRepository
                .findRegionSummaries(
                        currentUserProvider.currentUserId(),
                        AtlasViewMapper.toRegionLevel(scope),
                        normalizeCountryCode(countryCode),
                        filter)
                .stream()
                .map(AtlasViewMapper::toRegionSummaryView)
                .toList();
    }

    private static @Nullable String normalizeCountryCode(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
