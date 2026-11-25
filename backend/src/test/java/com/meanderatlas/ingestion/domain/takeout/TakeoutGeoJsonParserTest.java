package com.meanderatlas.ingestion.domain.takeout;

import static org.assertj.core.api.Assertions.assertThat;

import com.meanderatlas.ingestion.domain.FixtureArchiveSupport;
import com.meanderatlas.ingestion.domain.GoogleCid;
import com.meanderatlas.ingestion.domain.TakeoutTestData;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.VisitSource;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TakeoutGeoJsonParserTest {

    @Test
    void parsesSavedPlacesAndCrossMatchesCsvCidsWhileFilteringNullIsland(@TempDir Path tempDir)
            throws IOException {
        Path root = FixtureArchiveSupport.extractSyntheticTakeout(tempDir);

        List<ParsedSavedPlace> places =
                TakeoutGeoJsonParser.parseSavedPlaces(
                        root.resolve("Takeout/Mapy (Twoje miejsca)/Zapisane miejsca.json"));
        List<ParsedSavedCsvRow> csvRows =
                TakeoutCsvParser.parse(root.resolve("Takeout/Zapisano/Chcę odwiedzić.csv"));

        assertThat(places).hasSize(5);
        ParsedSavedPlace wawel = places.getFirst();
        assertThat(wawel.name()).isEqualTo("Wawel Royal Castle");
        assertThat(wawel.coordinates()).isEqualTo(new GeoPoint(50.054049, 19.935412));
        assertThat(wawel.googleCid()).isEqualTo(GoogleCid.fromUnsignedDecimal("536870913"));
        assertThat(wawel.countryCode()).isEqualTo("PL");
        assertThat(wawel.address()).isEqualTo("Wawel 5, 31-001 Krakow");
        assertThat(wawel.savedAt()).isEqualTo(Instant.parse("2025-05-01T10:00:00Z"));
        assertThat(wawel.listKind()).isEqualTo(ListKind.STARRED);

        Set<GoogleCid> csvCids =
                csvRows.stream()
                        .map(ParsedSavedCsvRow::googleCid)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        assertThat(places).extracting(ParsedSavedPlace::googleCid).allMatch(csvCids::contains);
    }

    @Test
    void parsesReviewsAsAutoVisitedRecordsFromSyntheticTakeout(@TempDir Path tempDir)
            throws IOException {
        Path root = FixtureArchiveSupport.extractSyntheticTakeout(tempDir);

        List<ParsedReview> reviews =
                TakeoutGeoJsonParser.parseReviews(
                        root.resolve("Takeout/Mapy (Twoje miejsca)/Opinie.json"));

        assertThat(reviews).hasSize(2);
        ParsedReview first = reviews.getFirst();
        assertThat(first.name()).isEqualTo("Wawel Royal Castle");
        assertThat(first.coordinates()).isEqualTo(new GeoPoint(50.054049, 19.935412));
        assertThat(first.googleCid()).isEqualTo(GoogleCid.fromUnsignedDecimal("536870913"));
        assertThat(first.countryCode()).isEqualTo("PL");
        assertThat(first.address()).isEqualTo("Wawel 5, 31-001 Krakow");
        assertThat(first.reviewedAt()).isEqualTo(Instant.parse("2025-06-15T12:00:00Z"));
        assertThat(first.starRating()).isEqualTo(5);
        assertThat(first.reviewText()).isEqualTo("Synthetic public landmark review.");
        assertThat(first.visitSource()).isEqualTo(VisitSource.REVIEW);
    }

    @Test
    void handlesNullIslandReviewsUnsigned64BitCidsAndPlEnFileDetection() {
        List<ParsedReview> reviews =
                TakeoutGeoJsonParser.parseReviews(
                        "Reviews.json", TakeoutTestData.nullIslandReviewsGeoJson());

        assertThat(reviews).hasSize(1);
        ParsedReview review = reviews.getFirst();
        assertThat(review.name()).isEqualTo("Ichiran Shibuya");
        assertThat(review.coordinates()).isNull();
        assertThat(review.googleCid())
                .isEqualTo(GoogleCid.fromUnsignedDecimal("18446744073709551615"));
        assertThat(review.starRating()).isEqualTo(4);
        assertThat(review.visitSource()).isEqualTo(VisitSource.REVIEW);

        assertThat(TakeoutGeoJsonParser.isSavedPlacesFile("Zapisane miejsca.json")).isTrue();
        assertThat(TakeoutGeoJsonParser.isSavedPlacesFile("Saved Places.json")).isTrue();
        assertThat(TakeoutGeoJsonParser.isSavedPlacesFile("Opinie.json")).isFalse();
        assertThat(TakeoutGeoJsonParser.isReviewsFile("Opinie.json")).isTrue();
        assertThat(TakeoutGeoJsonParser.isReviewsFile("Reviews.json")).isTrue();
        assertThat(TakeoutGeoJsonParser.isReviewsFile("Zapisane miejsca.json")).isFalse();
    }

    @Test
    void skipsMalformedSavedFeaturesAndHandlesLegacyKeysOutOfRangeRatingsAndBadDates() {
        assertThat(TakeoutGeoJsonParser.parseSavedPlaces("Saved Places.json", "{}")).isEmpty();
        assertThat(TakeoutGeoJsonParser.parseReviews("Reviews.json", "{}")).isEmpty();

        List<ParsedSavedPlace> saved =
                TakeoutGeoJsonParser.parseSavedPlaces(
                        "Saved Places.json", TakeoutTestData.malformedSavedPlacesGeoJson());
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().name()).isEqualTo("Legacy Keys Spot");
        assertThat(saved.getFirst().savedAt()).isNull();

        List<ParsedReview> reviews =
                TakeoutGeoJsonParser.parseReviews(
                        "Reviews.json", TakeoutTestData.malformedReviewsGeoJson());
        assertThat(reviews).hasSize(2);
        assertThat(reviews.getFirst().starRating()).isNull();
        assertThat(reviews.getFirst().reviewedAt()).isNull();
        assertThat(reviews.get(1).coordinates()).isNull();
        assertThat(reviews.get(1).starRating()).isNull();
    }
}
