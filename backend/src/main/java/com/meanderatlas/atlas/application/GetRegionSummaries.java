package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.RegionFilterQuery;
import com.meanderatlas.atlas.RegionScope;
import com.meanderatlas.atlas.RegionSummaryView;
import com.meanderatlas.atlas.domain.RegionCodeNormalizer;
import com.meanderatlas.atlas.domain.stats.AtlasStatsRepository;
import com.meanderatlas.atlas.domain.stats.RegionSummaryFilter;
import com.meanderatlas.shared.CurrentUserProvider;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetRegionSummaries {

    private final AtlasStatsRepository statsRepository;
    private final CurrentUserProvider currentUserProvider;

    public GetRegionSummaries(
            AtlasStatsRepository statsRepository, CurrentUserProvider currentUserProvider) {
        this.statsRepository = statsRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public List<RegionSummaryView> execute(
            RegionScope scope, @Nullable String countryCode, RegionFilterQuery query) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(query, "query must not be null");
        RegionSummaryFilter filter = AtlasViewMapper.toRegionSummaryFilter(query);
        return statsRepository
                .findRegionSummaries(
                        currentUserProvider.currentUserId(),
                        AtlasViewMapper.toRegionLevel(scope),
                        RegionCodeNormalizer.normalizeCountryCode(countryCode),
                        filter)
                .stream()
                .map(AtlasViewMapper::toRegionSummaryView)
                .toList();
    }
}
