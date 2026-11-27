package com.meanderatlas.ingestion.domain.enrichment;

import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.PlaceCategory;
import com.meanderatlas.shared.PlaceId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface OpenPoiCatalogPort {

    Map<PlaceId, EnrichmentLookupOutcome> enrichAnchoredBatch(
            List<AnchoredEnrichmentTarget> targets);

    EnrichmentLookupOutcome resolveUnanchored(UnanchoredLookupTarget target);

    record AnchoredEnrichmentTarget(
            PlaceId placeId,
            String canonicalName,
            GeoPoint coordinates,
            CoordSource coordSource,
            CategorySource categorySource,
            @Nullable String osmId,
            @Nullable String photoUrl,
            @Nullable String countryCode,
            @Nullable String cityName) {}

    record UnanchoredLookupTarget(
            UUID jobId, String rawTitle, @Nullable String rawUrl, @Nullable String countryHint) {}

    record OpenPoiCandidate(
            @Nullable String osmId,
            String name,
            GeoPoint coordinates,
            Map<String, String> tags,
            @Nullable String cityName,
            @Nullable String countryCode,
            @Nullable String openingHoursRaw) {}

    record PhotoLookupResult(@Nullable String photoUrl, @Nullable String source) {}

    record OpenPoiMatch(
            OpenPoiCandidate candidate,
            double score,
            double distanceMeters,
            PlaceCategory category,
            @Nullable String subcategory,
            @Nullable String cityName,
            @Nullable String photoUrl,
            @Nullable String openingHoursRaw) {}

    sealed interface EnrichmentLookupOutcome
            permits EnrichmentLookupOutcome.Matched,
                    EnrichmentLookupOutcome.NoMatch,
                    EnrichmentLookupOutcome.TransientFailure {

        record Matched(OpenPoiMatch match) implements EnrichmentLookupOutcome {}

        record NoMatch(String reason) implements EnrichmentLookupOutcome {}

        record TransientFailure(String error) implements EnrichmentLookupOutcome {}
    }
}
