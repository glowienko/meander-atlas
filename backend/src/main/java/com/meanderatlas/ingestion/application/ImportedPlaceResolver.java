package com.meanderatlas.ingestion.application;

import com.meanderatlas.atlas.AtlasFacade;
import com.meanderatlas.atlas.UpsertedPlaceView;
import com.meanderatlas.ingestion.domain.GoogleS2CellDecoder;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.PlaceId;
import java.util.Optional;

final class ImportedPlaceResolver {

    private final AtlasFacade atlasFacade;

    ImportedPlaceResolver(AtlasFacade atlasFacade) {
        this.atlasFacade = atlasFacade;
    }

    Optional<Resolution> attachByCid(ImportedPlaceDraft draft) {
        return Optional.ofNullable(draft.cid())
                .flatMap(cid -> atlasFacade.attachCsvMembershipByCid(draft.toAttachCommand(cid)))
                .map(Resolution.CidMatched::new);
    }

    Optional<Resolution> resolve(ImportedPlaceDraft draft) {
        return attachByCid(draft)
                .or(() ->
                        Optional.ofNullable(draft.titleCoords())
                                .map(coords -> materialize(draft, coords, CoordSource.TAKEOUT)))
                .or(() ->
                        GoogleS2CellDecoder.decodeFromUrl(draft.url())
                                .map(coords ->
                                        materialize(draft, coords, CoordSource.TAKEOUT_S2_APPROX)));
    }

    private Resolution materialize(
            ImportedPlaceDraft draft, GeoPoint coordinates, CoordSource coordSource) {
        UpsertedPlaceView upserted =
                atlasFacade.upsertImportedPlace(
                        draft.toUpsertCommand(coordinates, coordSource, null, null));
        return new Resolution.Materialized(upserted, coordSource);
    }

    sealed interface Resolution {

        PlaceId placeId();

        record CidMatched(PlaceId placeId) implements Resolution {}

        record Materialized(UpsertedPlaceView place, CoordSource coordSource)
                implements Resolution {

            @Override
            public PlaceId placeId() {
                return place.placeId();
            }
        }
    }
}
