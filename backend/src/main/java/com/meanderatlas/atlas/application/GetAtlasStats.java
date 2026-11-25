package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.AtlasStatsView;
import com.meanderatlas.atlas.domain.stats.AtlasStatsRepository;
import com.meanderatlas.shared.CurrentUserProvider;
import org.springframework.stereotype.Service;

@Service
public class GetAtlasStats {

    private final AtlasStatsRepository statsRepository;
    private final CurrentUserProvider currentUserProvider;

    public GetAtlasStats(
            AtlasStatsRepository statsRepository, CurrentUserProvider currentUserProvider) {
        this.statsRepository = statsRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public AtlasStatsView execute() {
        return AtlasViewMapper.toStatsView(
                statsRepository.fetchStats(currentUserProvider.currentUserId()));
    }
}
