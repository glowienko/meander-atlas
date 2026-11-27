package com.meanderatlas.ingestion.adapter.http;

import com.meanderatlas.ingestion.adapter.http.OpenDataHttp.OpenDataResponseException;
import com.meanderatlas.ingestion.domain.TitleCoordinateParser;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiMatcher;
import com.meanderatlas.shared.PlaceId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class CompositeOpenPoiCatalogAdapter implements OpenPoiCatalogPort {

    private static final Logger LOG = LoggerFactory.getLogger(CompositeOpenPoiCatalogAdapter.class);
    private static final Set<String> PHOTO_TAG_KEYS =
            Set.of("wikimedia_commons", "wikipedia", "wikidata", "image");

    private final OverpassPoiClient overpassPoiClient;
    private final PhotonPoiClient photonPoiClient;
    private final WikimediaPhotoClient wikimediaPhotoClient;

    CompositeOpenPoiCatalogAdapter(
            OverpassPoiClient overpassPoiClient,
            PhotonPoiClient photonPoiClient,
            WikimediaPhotoClient wikimediaPhotoClient) {
        this.overpassPoiClient = overpassPoiClient;
        this.photonPoiClient = photonPoiClient;
        this.wikimediaPhotoClient = wikimediaPhotoClient;
    }

    @Override
    public Map<PlaceId, EnrichmentLookupOutcome> enrichAnchoredBatch(
            List<AnchoredEnrichmentTarget> targets) {
        Map<Boolean, List<AnchoredEnrichmentTarget>> byCoordinateOnlyName =
                targets.stream()
                        .collect(
                                Collectors.partitioningBy(target ->
                                        TitleCoordinateParser.parse(target.canonicalName())
                                                .isPresent()));
        Stream<Map.Entry<PlaceId, EnrichmentLookupOutcome>> reverseEntries =
                byCoordinateOnlyName.getOrDefault(true, List.of()).stream()
                        .map(target -> Map.entry(target.placeId(), resolveByReverse(target)));
        Stream<Map.Entry<PlaceId, EnrichmentLookupOutcome>> namedEntries =
                overpassPoiClient
                        .partitionIntoBatches(byCoordinateOnlyName.getOrDefault(false, List.of()))
                        .stream()
                        .flatMap(this::enrichSpatialBatch);
        return Stream.concat(reverseEntries, namedEntries)
                .collect(
                        Collectors.toUnmodifiableMap(
                                Map.Entry::getKey, Map.Entry::getValue, (a, _) -> a));
    }

    @Override
    public EnrichmentLookupOutcome resolveUnanchored(UnanchoredLookupTarget target) {
        return lookup(
                "photon-global",
                () ->
                        OpenPoiMatcher.matchUnanchored(
                                        target, photonPoiClient.search(target.rawTitle(), null))
                                .map(match -> matched(match, null))
                                .orElseGet(() ->
                                        new EnrichmentLookupOutcome.NoMatch(
                                                "No confident global match in Photon")));
    }

    private Stream<Map.Entry<PlaceId, EnrichmentLookupOutcome>> enrichSpatialBatch(
            List<AnchoredEnrichmentTarget> batch) {
        List<OpenPoiCandidate> overpassCandidates;
        try {
            overpassCandidates = overpassPoiClient.fetchBatch(batch);
        } catch (RestClientException | OpenDataResponseException ex) {
            EnrichmentLookupOutcome overpassFailure =
                    new EnrichmentLookupOutcome.TransientFailure(describe("overpass", ex));
            LOG.warn(
                    "Overpass batch failed for {} targets ({}); falling back to Photon",
                    batch.size(),
                    overpassFailure);
            return batch.stream()
                    .map(target ->
                            Map.entry(target.placeId(), resolveViaPhoton(target, overpassFailure)));
        }
        return batch.stream()
                .map(target ->
                        Map.entry(
                                target.placeId(),
                                OpenPoiMatcher.matchAnchored(target, overpassCandidates)
                                        .map(match -> matched(match, target.photoUrl()))
                                        .orElseGet(() ->
                                                resolveViaPhoton(
                                                        target,
                                                        new EnrichmentLookupOutcome.NoMatch(
                                                                "No confident match in Overpass or Photon")))));
    }

    private EnrichmentLookupOutcome resolveViaPhoton(
            AnchoredEnrichmentTarget target, EnrichmentLookupOutcome whenUnmatched) {
        return lookup(
                "photon-anchored",
                () ->
                        OpenPoiMatcher.matchAnchored(
                                        target,
                                        photonPoiClient.search(
                                                target.canonicalName(), target.coordinates()))
                                .map(match -> matched(match, target.photoUrl()))
                                .orElse(whenUnmatched));
    }

    private EnrichmentLookupOutcome resolveByReverse(AnchoredEnrichmentTarget target) {
        return lookup(
                "photon-reverse",
                () ->
                        OpenPoiMatcher.matchReverseGeocoded(
                                        target.coordinates(),
                                        photonPoiClient.reverse(target.coordinates()))
                                .map(match -> matched(match, target.photoUrl()))
                                .orElseGet(() ->
                                        new EnrichmentLookupOutcome.NoMatch(
                                                "No reverse-geocoded place near coordinates")));
    }

    private static EnrichmentLookupOutcome lookup(
            String source, Supplier<EnrichmentLookupOutcome> action) {
        try {
            return action.get();
        } catch (RestClientException | OpenDataResponseException ex) {
            String error = describe(source, ex);
            LOG.warn("Enrichment lookup failed: {}", error);
            return new EnrichmentLookupOutcome.TransientFailure(error);
        }
    }

    private EnrichmentLookupOutcome matched(OpenPoiMatch match, @Nullable String existingPhotoUrl) {
        return new EnrichmentLookupOutcome.Matched(attachPhotoIfEligible(match, existingPhotoUrl));
    }

    private OpenPoiMatch attachPhotoIfEligible(
            OpenPoiMatch match, @Nullable String existingPhotoUrl) {
        Map<String, String> tags = match.candidate().tags();
        boolean needsPhoto = existingPhotoUrl == null || existingPhotoUrl.isBlank();
        if (!needsPhoto || tags.keySet().stream().noneMatch(PHOTO_TAG_KEYS::contains)) {
            return match;
        }
        try {
            return Optional.ofNullable(wikimediaPhotoClient.resolve(tags).photoUrl())
                    .map(match::withPhotoUrl)
                    .orElse(match);
        } catch (RestClientException | OpenDataResponseException ex) {
            LOG.warn("Keeping match without photo ({})", describe("wikimedia", ex));
            return match;
        }
    }

    private static String describe(String source, RuntimeException ex) {
        return source
                + ": "
                + Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getSimpleName());
    }
}
