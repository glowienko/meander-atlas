package com.meanderatlas.atlas;

import com.meanderatlas.atlas.application.AttachCsvMembershipByCid;
import com.meanderatlas.atlas.application.BulkUpdatePlaceVisitStatus;
import com.meanderatlas.atlas.application.GetAtlasHealth;
import com.meanderatlas.atlas.application.GetAtlasStats;
import com.meanderatlas.atlas.application.GetRegionDossier;
import com.meanderatlas.atlas.application.GetRegionMvtTile;
import com.meanderatlas.atlas.application.GetRegionSummaries;
import com.meanderatlas.atlas.application.QueryAtlasPlaces;
import com.meanderatlas.atlas.application.ReconcileTimelineVisit;
import com.meanderatlas.atlas.application.RestorePlaceVisitStates;
import com.meanderatlas.atlas.application.SeedRegionBoundaries;
import com.meanderatlas.atlas.application.UpdatePlaceVisitStatus;
import com.meanderatlas.atlas.application.UpsertImportedPlace;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class AtlasFacade {

    private final GetAtlasHealth getAtlasHealth;
    private final SeedRegionBoundaries seedRegionBoundaries;
    private final GetRegionMvtTile getRegionMvtTile;
    private final QueryAtlasPlaces queryAtlasPlaces;
    private final GetRegionSummaries getRegionSummaries;
    private final GetRegionDossier getRegionDossier;
    private final GetAtlasStats getAtlasStats;
    private final UpdatePlaceVisitStatus updatePlaceVisitStatus;
    private final BulkUpdatePlaceVisitStatus bulkUpdatePlaceVisitStatus;
    private final RestorePlaceVisitStates restorePlaceVisitStates;
    private final UpsertImportedPlace upsertImportedPlace;
    private final AttachCsvMembershipByCid attachCsvMembershipByCid;
    private final ReconcileTimelineVisit reconcileTimelineVisit;

    public AtlasFacade(
            GetAtlasHealth getAtlasHealth,
            SeedRegionBoundaries seedRegionBoundaries,
            GetRegionMvtTile getRegionMvtTile,
            QueryAtlasPlaces queryAtlasPlaces,
            GetRegionSummaries getRegionSummaries,
            GetRegionDossier getRegionDossier,
            GetAtlasStats getAtlasStats,
            UpdatePlaceVisitStatus updatePlaceVisitStatus,
            BulkUpdatePlaceVisitStatus bulkUpdatePlaceVisitStatus,
            RestorePlaceVisitStates restorePlaceVisitStates,
            UpsertImportedPlace upsertImportedPlace,
            AttachCsvMembershipByCid attachCsvMembershipByCid,
            ReconcileTimelineVisit reconcileTimelineVisit) {
        this.getAtlasHealth = getAtlasHealth;
        this.seedRegionBoundaries = seedRegionBoundaries;
        this.getRegionMvtTile = getRegionMvtTile;
        this.queryAtlasPlaces = queryAtlasPlaces;
        this.getRegionSummaries = getRegionSummaries;
        this.getRegionDossier = getRegionDossier;
        this.getAtlasStats = getAtlasStats;
        this.updatePlaceVisitStatus = updatePlaceVisitStatus;
        this.bulkUpdatePlaceVisitStatus = bulkUpdatePlaceVisitStatus;
        this.restorePlaceVisitStates = restorePlaceVisitStates;
        this.upsertImportedPlace = upsertImportedPlace;
        this.attachCsvMembershipByCid = attachCsvMembershipByCid;
        this.reconcileTimelineVisit = reconcileTimelineVisit;
    }

    public AtlasHealthView health() {
        return getAtlasHealth.execute();
    }

    public BoundarySeedResultView seedBoundaries(boolean force) {
        return seedRegionBoundaries.execute(force);
    }

    public byte[] regionMvtTile(
            RegionScope scope, int z, int x, int y, @Nullable String countryCode) {
        return getRegionMvtTile.execute(scope, z, x, y, countryCode);
    }

    public AtlasPlacesResponse places(AtlasPlaceFilterQuery filter) {
        return queryAtlasPlaces.execute(filter);
    }

    public List<RegionSummaryView> regionSummaries(
            RegionScope scope, @Nullable String countryCode, AtlasPlaceFilterQuery filter) {
        return getRegionSummaries.execute(scope, countryCode, filter);
    }

    public RegionDossierView regionDossier(RegionScope scope, String code) {
        return getRegionDossier.execute(scope, code);
    }

    public AtlasStatsView stats() {
        return getAtlasStats.execute();
    }

    public PlaceVisitStateView updatePlaceVisitStatus(
            UUID placeId, UpdatePlaceVisitStatusRequest request) {
        return updatePlaceVisitStatus.execute(placeId, request);
    }

    public BulkVisitStatusResultView bulkUpdatePlaceVisitStatus(BulkVisitStatusRequest request) {
        return bulkUpdatePlaceVisitStatus.execute(request);
    }

    public RestoreVisitStatesResultView restorePlaceVisitStates(RestoreVisitStatesRequest request) {
        return restorePlaceVisitStates.execute(request);
    }

    public UpsertedPlaceView upsertImportedPlace(ImportedPlaceUpsertCommand command) {
        return upsertImportedPlace.execute(command);
    }

    public boolean attachCsvMembershipByCid(AttachCsvMembershipCommand command) {
        return attachCsvMembershipByCid.execute(command);
    }

    public TimelineReconciliationOutcome reconcileTimelineVisit(TimelineVisitSignal signal) {
        return reconcileTimelineVisit.execute(signal);
    }
}
