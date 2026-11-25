package com.meanderatlas.atlas.domain.stats;

import com.meanderatlas.atlas.domain.RegionLevel;
import com.meanderatlas.atlas.domain.places.PlaceQueryFilter;
import com.meanderatlas.shared.UserId;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public interface AtlasStatsRepository {

    List<RegionSummary> findRegionSummaries(
            UserId userId,
            RegionLevel level,
            @Nullable String countryCode,
            PlaceQueryFilter filter);

    Optional<RegionSummary> findRegionSummary(UserId userId, RegionLevel level, String code);

    AtlasStats fetchStats(UserId userId);
}
