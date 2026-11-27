package com.meanderatlas.ingestion.domain.enrichment;

import com.meanderatlas.ingestion.domain.TextNormalizer;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.AnchoredEnrichmentTarget;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.OpenPoiCandidate;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.OpenPoiMatch;
import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.UnanchoredLookupTarget;
import com.meanderatlas.ingestion.domain.enrichment.OsmTagCategoryMapper.OsmCategoryMapping;
import com.meanderatlas.shared.CoordSource;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public final class OpenPoiMatcher {

    private static final double EXACT_RADIUS_METERS = 600.0;
    private static final double APPROX_RADIUS_METERS = 2500.0;
    private static final double EXACT_MID_DISTANCE_METERS = 250.0;
    private static final double EXACT_MIN_NAME_NEAR = 0.55;
    private static final double EXACT_MIN_NAME_FAR = 0.72;
    private static final double EXACT_MIN_COMBINED = 0.65;
    private static final double APPROX_MIN_NAME = 0.65;
    private static final double APPROX_MIN_COMBINED = 0.70;
    private static final double UNANCHORED_MIN_NAME = 0.80;
    private static final double AMBIGUITY_DISTANCE_METERS = 2000.0;
    private static final double AMBIGUITY_SCORE_DELTA = 0.05;
    private static final double TOKEN_FUZZY_THRESHOLD = 0.85;
    private static final Set<String> GENERIC_PLACEHOLDERS =
            Set.of("dropped pin", "pinned location", "reviewed place", "pinezka", "saved places");
    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}\\s]+");
    private static final Pattern COORDINATE_TEXT =
            Pattern.compile("^[+-]?\\d+(?:\\.\\d+)?\\s*,\\s*[+-]?\\d+(?:\\.\\d+)?$");

    private OpenPoiMatcher() {}

    public static boolean isGenericPlaceholder(@Nullable String rawTitle) {
        String folded = normalizeName(rawTitle);
        if (folded.isEmpty()) {
            return true;
        }
        if (GENERIC_PLACEHOLDERS.contains(folded)) {
            return true;
        }
        String trimmed = rawTitle == null ? "" : rawTitle.trim();
        return COORDINATE_TEXT.matcher(trimmed).matches();
    }

    public static Optional<OpenPoiMatch> matchAnchored(
            AnchoredEnrichmentTarget target, List<OpenPoiCandidate> candidates) {
        if (candidates.isEmpty() || isGenericPlaceholder(target.canonicalName())) {
            return Optional.empty();
        }
        String targetNorm = normalizeName(target.canonicalName());
        boolean exactAnchor =
                target.coordSource() == CoordSource.TAKEOUT
                        || target.coordSource() == CoordSource.TIMELINE;
        double maxRadius = exactAnchor ? EXACT_RADIUS_METERS : APPROX_RADIUS_METERS;
        return candidates.stream()
                .map(candidate ->
                        scoreAnchored(target, targetNorm, candidate, exactAnchor, maxRadius))
                .flatMap(Optional::stream)
                .min(scoredComparator())
                .map(ScoredCandidate::toMatch);
    }

    public static Optional<OpenPoiMatch> matchUnanchored(
            UnanchoredLookupTarget target, List<OpenPoiCandidate> candidates) {
        if (candidates.isEmpty() || isGenericPlaceholder(target.rawTitle())) {
            return Optional.empty();
        }
        String targetNorm = normalizeName(target.rawTitle());
        String countryHint = TextNormalizer.trimToNull(target.countryHint());
        List<ScoredCandidate> passing =
                candidates.stream()
                        .filter(candidate ->
                                matchesCountryHint(countryHint, candidate.countryCode()))
                        .map(candidate -> scoreUnanchored(targetNorm, candidate))
                        .flatMap(Optional::stream)
                        .sorted(scoredComparator())
                        .toList();
        if (passing.isEmpty()) {
            return Optional.empty();
        }
        if (passing.size() >= 2) {
            ScoredCandidate first = passing.get(0);
            ScoredCandidate second = passing.get(1);
            double separation =
                    GeoDistance.metersBetween(
                            first.candidate().coordinates(), second.candidate().coordinates());
            if (separation > AMBIGUITY_DISTANCE_METERS
                    && (first.combinedScore() - second.combinedScore()) <= AMBIGUITY_SCORE_DELTA) {
                return Optional.empty();
            }
        }
        return Optional.of(passing.getFirst().toMatch());
    }

    private static Optional<ScoredCandidate> scoreAnchored(
            AnchoredEnrichmentTarget target,
            String targetNorm,
            OpenPoiCandidate candidate,
            boolean exactAnchor,
            double maxRadius) {
        double distanceMeters =
                GeoDistance.metersBetween(target.coordinates(), candidate.coordinates());
        if (distanceMeters > maxRadius) {
            return Optional.empty();
        }
        double nameScore = bestNameSimilarity(targetNorm, candidate);
        double distanceScore = 1.0 - (distanceMeters / maxRadius);
        if (exactAnchor) {
            double minName =
                    distanceMeters > EXACT_MID_DISTANCE_METERS
                            ? EXACT_MIN_NAME_FAR
                            : EXACT_MIN_NAME_NEAR;
            double combined = 0.72 * nameScore + 0.28 * distanceScore;
            if (nameScore < minName || combined < EXACT_MIN_COMBINED) {
                return Optional.empty();
            }
            return Optional.of(new ScoredCandidate(candidate, combined, nameScore, distanceMeters));
        }
        double combined = 0.78 * nameScore + 0.22 * distanceScore;
        if (nameScore < APPROX_MIN_NAME || combined < APPROX_MIN_COMBINED) {
            return Optional.empty();
        }
        return Optional.of(new ScoredCandidate(candidate, combined, nameScore, distanceMeters));
    }

    private static Optional<ScoredCandidate> scoreUnanchored(
            String targetNorm, OpenPoiCandidate candidate) {
        double nameScore = bestNameSimilarity(targetNorm, candidate);
        if (nameScore < UNANCHORED_MIN_NAME) {
            return Optional.empty();
        }
        return Optional.of(new ScoredCandidate(candidate, nameScore, nameScore, 0.0));
    }

    private static boolean matchesCountryHint(
            @Nullable String countryHint, @Nullable String candidateCountryCode) {
        if (countryHint == null || candidateCountryCode == null || candidateCountryCode.isBlank()) {
            return true;
        }
        return countryHint.equalsIgnoreCase(candidateCountryCode.trim());
    }

    private static Comparator<ScoredCandidate> scoredComparator() {
        return Comparator.comparingDouble(ScoredCandidate::combinedScore)
                .reversed()
                .thenComparing(Comparator.comparingDouble(ScoredCandidate::nameScore).reversed())
                .thenComparingDouble(ScoredCandidate::distanceMeters)
                .thenComparing(
                        scored -> Objects.requireNonNullElse(scored.candidate().osmId(), ""));
    }

    private static double bestNameSimilarity(String targetNorm, OpenPoiCandidate candidate) {
        Map<String, String> tags = candidate.tags();
        return Stream.of(
                        candidate.name(),
                        tags.get("name"),
                        tags.get("name:en"),
                        tags.get("int_name"),
                        tags.get("alt_name"),
                        tags.get("short_name"),
                        tags.get("official_name"))
                .filter(Objects::nonNull)
                .map(OpenPoiMatcher::normalizeName)
                .filter(name -> !name.isEmpty())
                .distinct()
                .mapToDouble(candidateNorm -> nameSimilarity(targetNorm, candidateNorm))
                .max()
                .orElse(0.0);
    }

    static double nameSimilarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        if (a.equals(b)) {
            return 1.0;
        }
        List<String> tokensA = tokenize(a);
        List<String> tokensB = tokenize(b);
        if (hasContainmentMatch(a, b, tokensA, tokensB)) {
            return 0.90;
        }
        double tokenDice = computeTokenDice(tokensA, tokensB);
        double levenshteinSim = computeLevenshteinSimilarity(a, b);
        return 0.55 * tokenDice + 0.45 * levenshteinSim;
    }

    private static boolean hasContainmentMatch(
            String a, String b, List<String> tokensA, List<String> tokensB) {
        if (tokensA.isEmpty() || tokensB.isEmpty()) {
            return false;
        }
        String shorterStr = a.length() <= b.length() ? a : b;
        String longerStr = a.length() <= b.length() ? b : a;
        if (shorterStr.length() >= 5 && isBoundarySubstring(longerStr, shorterStr)) {
            return true;
        }
        List<String> shorterTokens = tokensA.size() <= tokensB.size() ? tokensA : tokensB;
        List<String> longerTokens = tokensA.size() <= tokensB.size() ? tokensB : tokensA;
        boolean hasSubstantialToken = shorterTokens.stream().anyMatch(token -> token.length() >= 4);
        if (!hasSubstantialToken) {
            return false;
        }
        return shorterTokens.stream().allMatch(longerTokens::contains);
    }

    private static boolean isBoundarySubstring(String longer, String shorter) {
        return longer.equals(shorter)
                || longer.startsWith(shorter + " ")
                || longer.endsWith(" " + shorter)
                || longer.contains(" " + shorter + " ");
    }

    private static double computeTokenDice(List<String> tokensA, List<String> tokensB) {
        if (tokensA.isEmpty() || tokensB.isEmpty()) {
            return 0.0;
        }
        long matchesA =
                tokensA.stream()
                        .filter(ta ->
                                tokensB.stream()
                                        .anyMatch(tb ->
                                                computeLevenshteinSimilarity(ta, tb)
                                                        >= TOKEN_FUZZY_THRESHOLD))
                        .count();
        long matchesB =
                tokensB.stream()
                        .filter(tb ->
                                tokensA.stream()
                                        .anyMatch(ta ->
                                                computeLevenshteinSimilarity(tb, ta)
                                                        >= TOKEN_FUZZY_THRESHOLD))
                        .count();
        double overlap = 0.5 * (matchesA + matchesB);
        return (2.0 * overlap) / (tokensA.size() + tokensB.size());
    }

    private static double computeLevenshteinSimilarity(String a, String b) {
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) {
            return 1.0;
        }
        int distance = levenshteinDistance(a, b);
        return 1.0 - ((double) distance / maxLen);
    }

    private static int levenshteinDistance(String a, String b) {
        int lenA = a.length();
        int lenB = b.length();
        int[] prev = new int[lenB + 1];
        int[] curr = new int[lenB + 1];
        for (int j = 0; j <= lenB; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= lenA; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= lenB; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            System.arraycopy(curr, 0, prev, 0, lenB + 1);
        }
        return prev[lenB];
    }

    private static String normalizeName(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String folded = TextNormalizer.normalizeFolded(raw);
        String stripped = NON_ALNUM.matcher(folded).replaceAll(" ");
        return TextNormalizer.normalizeSpaces(stripped);
    }

    private static List<String> tokenize(String normalized) {
        if (normalized.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(normalized.split(" ")).filter(token -> !token.isEmpty()).toList();
    }

    private record ScoredCandidate(
            OpenPoiCandidate candidate,
            double combinedScore,
            double nameScore,
            double distanceMeters) {

        OpenPoiMatch toMatch() {
            OsmCategoryMapping mapping = OsmTagCategoryMapper.map(candidate.tags());
            return new OpenPoiMatch(
                    candidate,
                    combinedScore,
                    distanceMeters,
                    mapping.category(),
                    mapping.subcategory(),
                    candidate.cityName(),
                    null,
                    candidate.openingHoursRaw());
        }
    }
}
