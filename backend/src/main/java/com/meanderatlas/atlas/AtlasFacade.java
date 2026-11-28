package com.meanderatlas.atlas;

import com.meanderatlas.atlas.application.ApplyPlaceEnrichment;
import com.meanderatlas.atlas.application.AttachCsvMembershipByCid;
import com.meanderatlas.atlas.application.BulkAssignPlacesToList;
import com.meanderatlas.atlas.application.BulkUpdatePlaceVisitStatus;
import com.meanderatlas.atlas.application.FindPlaceEnrichmentTargets;
import com.meanderatlas.atlas.application.FindPlacesNeedingEnrichment;
import com.meanderatlas.atlas.application.GetAtlasHealth;
import com.meanderatlas.atlas.application.GetAtlasStats;
import com.meanderatlas.atlas.application.GetRegionDossier;
import com.meanderatlas.atlas.application.GetRegionMvtTile;
import com.meanderatlas.atlas.application.GetRegionSummaries;
import com.meanderatlas.atlas.application.QueryAtlasPlaces;
import com.meanderatlas.atlas.application.ReconcileTimelineVisit;
import com.meanderatlas.atlas.application.RestorePlaceVisitStates;
import com.meanderatlas.atlas.application.SeedRegionBoundaries;
import com.meanderatlas.atlas.application.UpdatePlaceCategory;
import com.meanderatlas.atlas.application.UpdatePlaceLists;
import com.meanderatlas.atlas.application.UpdatePlaceVisitStatus;
import com.meanderatlas.atlas.application.UpsertImportedPlace;
import com.meanderatlas.shared.PlaceId;
import java.util.List;
import java.util.Optional;
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
    private final UpdatePlaceCategory updatePlaceCategory;
    private final UpdatePlaceLists updatePlaceLists;
    private final BulkAssignPlacesToList bulkAssignPlacesToList;
    private final BulkUpdatePlaceVisitStatus bulkUpdatePlaceVisitStatus;
    private final RestorePlaceVisitStates restorePlaceVisitStates;
    private final UpsertImportedPlace upsertImportedPlace;
    private final AttachCsvMembershipByCid attachCsvMembershipByCid;
    private final ReconcileTimelineVisit reconcileTimelineVisit;
    private final FindPlacesNeedingEnrichment findPlacesNeedingEnrichment;
    private final FindPlaceEnrichmentTargets findPlaceEnrichmentTargets;
    private final ApplyPlaceEnrichment applyPlaceEnrichment;

    public AtlasFacade(
            GetAtlasHealth getAtlasHealth,
            SeedRegionBoundaries seedRegionBoundaries,
            GetRegionMvtTile getRegionMvtTile,
            QueryAtlasPlaces queryAtlasPlaces,
            GetRegionSummaries getRegionSummaries,
            GetRegionDossier getRegionDossier,
            GetAtlasStats getAtlasStats,
            UpdatePlaceVisitStatus updatePlaceVisitStatus,
            UpdatePlaceCategory updatePlaceCategory,
            UpdatePlaceLists updatePlaceLists,
            BulkAssignPlacesToList bulkAssignPlacesToList,
            BulkUpdatePlaceVisitStatus bulkUpdatePlaceVisitStatus,
            RestorePlaceVisitStates restorePlaceVisitStates,
            UpsertImportedPlace upsertImportedPlace,
            AttachCsvMembershipByCid attachCsvMembershipByCid,
            ReconcileTimelineVisit reconcileTimelineVisit,
            FindPlacesNeedingEnrichment findPlacesNeedingEnrichment,
            FindPlaceEnrichmentTargets findPlaceEnrichmentTargets,
            ApplyPlaceEnrichment applyPlaceEnrichment) {
        this.getAtlasHealth = getAtlasHealth;
        this.seedRegionBoundaries = seedRegionBoundaries;
        this.getRegionMvtTile = getRegionMvtTile;
        this.queryAtlasPlaces = queryAtlasPlaces;
        this.getRegionSummaries = getRegionSummaries;
        this.getRegionDossier = getRegionDossier;
        this.getAtlasStats = getAtlasStats;
        this.updatePlaceVisitStatus = updatePlaceVisitStatus;
        this.updatePlaceCategory = updatePlaceCategory;
        this.updatePlaceLists = updatePlaceLists;
        this.bulkAssignPlacesToList = bulkAssignPlacesToList;
        this.bulkUpdatePlaceVisitStatus = bulkUpdatePlaceVisitStatus;
        this.restorePlaceVisitStates = restorePlaceVisitStates;
        this.upsertImportedPlace = upsertImportedPlace;
        this.attachCsvMembershipByCid = attachCsvMembershipByCid;
        this.reconcileTimelineVisit = reconcileTimelineVisit;
        this.findPlacesNeedingEnrichment = findPlacesNeedingEnrichment;
        this.findPlaceEnrichmentTargets = findPlaceEnrichmentTargets;
        this.applyPlaceEnrichment = applyPlaceEnrichment;
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
            RegionScope scope, @Nullable String countryCode, RegionFilterQuery filter) {
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

    public PlaceCategoryStateView updatePlaceCategory(
            UUID placeId, UpdatePlaceCategoryRequest request) {
        return updatePlaceCategory.execute(placeId, request);
    }

    public PlaceListsStateView updatePlaceLists(UUID placeId, UpdatePlaceListsRequest request) {
        return updatePlaceLists.execute(placeId, request);
    }

    public BulkAssignListResultView bulkAssignPlacesToList(BulkAssignListRequest request) {
        return bulkAssignPlacesToList.execute(request);
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

    public Optional<PlaceId> attachCsvMembershipByCid(AttachCsvMembershipCommand command) {
        return attachCsvMembershipByCid.execute(command);
    }

    public TimelineReconciliationOutcome reconcileTimelineVisit(TimelineVisitSignal signal) {
        return reconcileTimelineVisit.execute(signal);
    }

    public List<PlaceId> findPlaceIdsNeedingEnrichment() {
        return findPlacesNeedingEnrichment.execute(null);
    }

    public List<PlaceId> findPlaceIdsNeedingEnrichment(List<PlaceId> candidates) {
        return candidates.isEmpty() ? List.of() : findPlacesNeedingEnrichment.execute(candidates);
    }

    public List<PlaceEnrichmentTargetView> findEnrichmentTargets(List<PlaceId> placeIds) {
        return findPlaceEnrichmentTargets.execute(placeIds);
    }

    public PlaceEnrichmentResultView applyPlaceEnrichment(PlaceEnrichmentCommand command) {
        return applyPlaceEnrichment.execute(command);
    }
}
