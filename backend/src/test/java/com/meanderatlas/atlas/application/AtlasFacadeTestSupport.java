package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.AtlasFacade;
import com.meanderatlas.atlas.domain.boundaries.RegionBoundaryRepository;
import com.meanderatlas.atlas.domain.places.AtlasPlaceRepository;
import com.meanderatlas.atlas.domain.stats.AtlasStatsRepository;
import com.meanderatlas.shared.CurrentUserProvider;
import java.nio.file.Path;
import org.springframework.transaction.support.TransactionOperations;

public final class AtlasFacadeTestSupport {

    private AtlasFacadeTestSupport() {}

    public static AtlasFacade create(
            AtlasPlaceRepository placeRepo,
            RegionBoundaryRepository boundaryRepo,
            AtlasStatsRepository statsRepo,
            Path boundariesDir,
            CurrentUserProvider userProvider) {
        ImportedPlaceMerger merger = new ImportedPlaceMerger(placeRepo);
        return new AtlasFacade(
                new GetAtlasHealth(() -> "3.5.3"),
                new SeedRegionBoundaries(
                        boundaryRepo,
                        boundariesDir.toString(),
                        TransactionOperations.withoutTransaction()),
                new GetRegionMvtTile(boundaryRepo),
                new QueryAtlasPlaces(placeRepo, userProvider),
                new GetRegionSummaries(statsRepo, userProvider),
                new GetRegionDossier(statsRepo, placeRepo, userProvider),
                new GetAtlasStats(statsRepo, userProvider),
                new UpdatePlaceVisitStatus(placeRepo, userProvider),
                new BulkUpdatePlaceVisitStatus(placeRepo, userProvider),
                new RestorePlaceVisitStates(placeRepo, userProvider),
                new UpsertImportedPlace(placeRepo, userProvider, merger),
                new AttachCsvMembershipByCid(placeRepo, userProvider, merger),
                new ReconcileTimelineVisit(placeRepo, userProvider));
    }
}
