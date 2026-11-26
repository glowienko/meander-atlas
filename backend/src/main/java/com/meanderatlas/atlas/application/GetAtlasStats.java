package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.AtlasStatsView;
import com.meanderatlas.atlas.domain.stats.AtlasStatsRepository;
import com.meanderatlas.shared.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetAtlasStats {

    private final AtlasStatsRepository statsRepository;
    private final CurrentUserProvider currentUserProvider;

    public GetAtlasStats(
            AtlasStatsRepository statsRepository, CurrentUserProvider currentUserProvider) {
        this.statsRepository = statsRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public AtlasStatsView execute() {
        return AtlasViewMapper.toStatsView(
                statsRepository.fetchStats(currentUserProvider.currentUserId()));
    }
}
