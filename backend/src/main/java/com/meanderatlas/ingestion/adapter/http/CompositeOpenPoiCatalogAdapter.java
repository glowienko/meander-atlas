package com.meanderatlas.ingestion.adapter.http;

import com.meanderatlas.ingestion.adapter.http.OverpassPoiClient.OverpassTransientException;
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
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

@Component
public class CompositeOpenPoiCatalogAdapter implements OpenPoiCatalogPort {

    private static final long MAX_RETRY_AFTER_MS = 60_000L;
    private static final Set<String> PHOTO_TAG_KEYS =
            Set.of("wikimedia_commons", "wikipedia", "wikidata", "image");

    private final OverpassPoiClient overpassPoiClient;
    private final PhotonPoiClient photonPoiClient;
    private final WikimediaPhotoClient wikimediaPhotoClient;
    private final EnrichmentProperties properties;
    private final Sleeper sleeper;

    @Autowired
    public CompositeOpenPoiCatalogAdapter(
            OverpassPoiClient overpassPoiClient,
            PhotonPoiClient photonPoiClient,
            WikimediaPhotoClient wikimediaPhotoClient,
            EnrichmentProperties properties) {
        this(overpassPoiClient, photonPoiClient, wikimediaPhotoClient, properties, Thread::sleep);
    }

    CompositeOpenPoiCatalogAdapter(
            OverpassPoiClient overpassPoiClient,
            PhotonPoiClient photonPoiClient,
            WikimediaPhotoClient wikimediaPhotoClient,
            EnrichmentProperties properties,
            Sleeper sleeper) {
        this.overpassPoiClient = overpassPoiClient;
        this.photonPoiClient = photonPoiClient;
        this.wikimediaPhotoClient = wikimediaPhotoClient;
        this.properties = properties;
        this.sleeper = sleeper;
    }

    @Override
    public Map<PlaceId, EnrichmentLookupOutcome> enrichAnchoredBatch(
            List<AnchoredEnrichmentTarget> targets) {
        if (targets.isEmpty()) {
            return Map.of();
        }
        List<List<AnchoredEnrichmentTarget>> batches =
                overpassPoiClient.partitionIntoBatches(targets);
        return batches.stream()
                .flatMap(batch -> enrichSingleSpatialBatch(batch).entrySet().stream())
                .collect(
                        Collectors.toUnmodifiableMap(
                                Map.Entry::getKey, Map.Entry::getValue, (a, _) -> a));
    }

    @Override
    public EnrichmentLookupOutcome resolveUnanchored(UnanchoredLookupTarget target) {
        RetryableResult<List<OpenPoiCandidate>> photonResult =
                executeWithRetry(
                        () -> photonPoiClient.search(target.rawTitle(), null), "photon-global");
        if (photonResult.error() != null) {
            return new EnrichmentLookupOutcome.TransientFailure(photonResult.error());
        }
        List<OpenPoiCandidate> candidates =
                Objects.requireNonNullElse(photonResult.value(), List.of());
        Optional<OpenPoiMatch> matched = OpenPoiMatcher.matchUnanchored(target, candidates);
        if (matched.isEmpty()) {
            return new EnrichmentLookupOutcome.NoMatch("No confident global match in Photon");
        }
        OpenPoiMatch withPhoto = attachPhotoIfEligible(matched.get(), null);
        return new EnrichmentLookupOutcome.Matched(withPhoto);
    }

    private Map<PlaceId, EnrichmentLookupOutcome> enrichSingleSpatialBatch(
            List<AnchoredEnrichmentTarget> batch) {
        RetryableResult<List<OpenPoiCandidate>> overpassResult =
                executeWithRetry(() -> overpassPoiClient.fetchBatch(batch), "overpass");
        if (overpassResult.error() != null) {
            EnrichmentLookupOutcome failure =
                    new EnrichmentLookupOutcome.TransientFailure(overpassResult.error());
            return batch.stream()
                    .collect(
                            Collectors.toUnmodifiableMap(
                                    AnchoredEnrichmentTarget::placeId, _ -> failure, (a, _) -> a));
        }
        List<OpenPoiCandidate> overpassCandidates =
                Objects.requireNonNullElse(overpassResult.value(), List.of());
        return batch.stream()
                .collect(
                        Collectors.toUnmodifiableMap(
                                AnchoredEnrichmentTarget::placeId,
                                target -> resolveAnchoredTarget(target, overpassCandidates),
                                (a, _) -> a));
    }

    private EnrichmentLookupOutcome resolveAnchoredTarget(
            AnchoredEnrichmentTarget target, List<OpenPoiCandidate> overpassCandidates) {
        Optional<OpenPoiMatch> overpassMatch =
                OpenPoiMatcher.matchAnchored(target, overpassCandidates);
        if (overpassMatch.isPresent()) {
            return new EnrichmentLookupOutcome.Matched(
                    attachPhotoIfEligible(overpassMatch.get(), target.photoUrl()));
        }
        RetryableResult<List<OpenPoiCandidate>> photonResult =
                executeWithRetry(
                        () -> photonPoiClient.search(target.canonicalName(), target.coordinates()),
                        "photon-anchored");
        if (photonResult.error() != null) {
            return new EnrichmentLookupOutcome.TransientFailure(photonResult.error());
        }
        List<OpenPoiCandidate> photonCandidates =
                Objects.requireNonNullElse(photonResult.value(), List.of());
        Optional<OpenPoiMatch> photonMatch = OpenPoiMatcher.matchAnchored(target, photonCandidates);
        if (photonMatch.isEmpty()) {
            return new EnrichmentLookupOutcome.NoMatch("No confident match in Overpass or Photon");
        }
        return new EnrichmentLookupOutcome.Matched(
                attachPhotoIfEligible(photonMatch.get(), target.photoUrl()));
    }

    private OpenPoiMatch attachPhotoIfEligible(
            OpenPoiMatch match, @Nullable String existingPhotoUrl) {
        if (existingPhotoUrl != null && !existingPhotoUrl.isBlank()) {
            return match;
        }
        Map<String, String> tags = match.candidate().tags();
        boolean hasPhotoTags = tags.keySet().stream().anyMatch(PHOTO_TAG_KEYS::contains);
        if (!hasPhotoTags) {
            return match;
        }
        pausePolitely();
        PhotoLookupResult photo = wikimediaPhotoClient.resolve(tags);
        if (photo.photoUrl() == null) {
            return match;
        }
        return new OpenPoiMatch(
                match.candidate(),
                match.score(),
                match.distanceMeters(),
                match.category(),
                match.subcategory(),
                match.cityName(),
                photo.photoUrl(),
                match.openingHoursRaw());
    }

    private <T> RetryableResult<T> executeWithRetry(Supplier<T> action, String sourceName) {
        int maxAttempts = Math.max(1, properties.maxRetries() + 1);
        String lastError = sourceName + " failed";
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            pausePolitely();
            try {
                return new RetryableResult<>(action.get(), null);
            } catch (HttpStatusCodeException ex) {
                HttpStatusCode status = ex.getStatusCode();
                lastError = sourceName + " HTTP " + status.value();
                if (!isRetryableStatus(status) || attempt + 1 >= maxAttempts) {
                    return new RetryableResult<>(null, lastError);
                }
                sleepQuietly(computeRetryDelayMs(attempt, ex.getResponseHeaders()));
            } catch (ResourceAccessException | OverpassTransientException ex) {
                lastError =
                        Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getSimpleName());
                if (attempt + 1 >= maxAttempts) {
                    return new RetryableResult<>(null, lastError);
                }
                sleepQuietly(computeRetryDelayMs(attempt, null));
            } catch (RestClientException ex) {
                lastError =
                        Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getSimpleName());
                return new RetryableResult<>(null, lastError);
            }
        }
        return new RetryableResult<>(null, lastError);
    }

    private static boolean isRetryableStatus(HttpStatusCode status) {
        return status.value() == 429 || status.is5xxServerError();
    }

    private long computeRetryDelayMs(int attempt, @Nullable HttpHeaders headers) {
        long baseDelay = Math.max(0L, properties.requestDelayMs());
        if (baseDelay == 0L) {
            return 0L;
        }
        if (headers != null) {
            String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
            if (retryAfter != null) {
                try {
                    long seconds = Long.parseLong(retryAfter.trim());
                    if (seconds >= 0L) {
                        return Math.min(MAX_RETRY_AFTER_MS, seconds * 1000L);
                    }
                } catch (NumberFormatException ignored) {
                    return Math.min(MAX_RETRY_AFTER_MS, baseDelay * (1L << Math.min(attempt, 5)));
                }
            }
        }
        return Math.min(MAX_RETRY_AFTER_MS, baseDelay * (1L << Math.min(attempt, 5)));
    }

    private void pausePolitely() {
        long delayMs = properties.requestDelayMs();
        if (delayMs > 0L) {
            sleepQuietly(delayMs);
        }
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0L) {
            return;
        }
        try {
            sleeper.sleepMillis(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleepMillis(long millis) throws InterruptedException;
    }

    private record RetryableResult<T>(@Nullable T value, @Nullable String error) {}
}
