package com.meanderatlas.ingestion.application;

import com.meanderatlas.atlas.AttachCsvMembershipCommand;
import com.meanderatlas.atlas.ImportedPlaceUpsertCommand;
import com.meanderatlas.ingestion.domain.GoogleCid;
import com.meanderatlas.ingestion.domain.TextNormalizer;
import com.meanderatlas.ingestion.domain.TitleCoordinateParser;
import com.meanderatlas.ingestion.domain.classification.PlaceCategoryClassifier;
import com.meanderatlas.ingestion.domain.classification.PlaceClassification;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

record ImportedPlaceDraft(
        String accountLabel,
        String canonicalTitle,
        @Nullable String url,
        @Nullable Long cid,
        String listName,
        ListKind listKind,
        @Nullable String note,
        @Nullable String tags,
        PlaceClassification classification,
        @Nullable GeoPoint titleCoords,
        @Nullable Instant savedAt,
        @Nullable VisitStatus visitStatus,
        @Nullable VisitSource visitSource,
        @Nullable Instant visitedAt) {

    static final String LIST_NAME_SAVED_DEFAULT = "Saved Places";
    private static final String LIST_NAME_REVIEWS = "Google Reviews";
    private static final String FALLBACK_REVIEW_NAME = "Reviewed place";

    static ImportedPlaceDraft review(
            String accountLabel,
            @Nullable String rawName,
            @Nullable String reviewText,
            @Nullable String url,
            @Nullable Long cid,
            @Nullable Instant reviewedAt) {
        String canonicalName =
                Objects.requireNonNullElse(
                        TextNormalizer.trimToNull(rawName), FALLBACK_REVIEW_NAME);
        return new ImportedPlaceDraft(
                accountLabel,
                canonicalName,
                url,
                cid,
                LIST_NAME_REVIEWS,
                ListKind.REVIEW,
                reviewText,
                null,
                PlaceCategoryClassifier.classify(canonicalName, reviewText, null),
                TitleCoordinateParser.parse(rawName).orElse(null),
                reviewedAt,
                VisitStatus.VISITED,
                VisitSource.REVIEW,
                reviewedAt);
    }

    static ImportedPlaceDraft saved(
            String accountLabel,
            @Nullable String title,
            @Nullable String url,
            @Nullable Long cid,
            String listName,
            ListKind listKind,
            @Nullable String note,
            @Nullable String tags,
            @Nullable Instant savedAt) {
        String canonicalTitle = Objects.requireNonNullElse(title, LIST_NAME_SAVED_DEFAULT);
        return new ImportedPlaceDraft(
                accountLabel,
                canonicalTitle,
                url,
                cid,
                listName,
                listKind,
                note,
                tags,
                PlaceCategoryClassifier.classify(canonicalTitle, note, tags),
                TitleCoordinateParser.parse(title).orElse(null),
                savedAt,
                null,
                null,
                null);
    }

    static @Nullable Long cidBits(@Nullable GoogleCid cid) {
        return cid == null ? null : cid.unsignedBits();
    }

    ImportedPlaceDraft withClassification(PlaceClassification override) {
        return new ImportedPlaceDraft(
                accountLabel,
                canonicalTitle,
                url,
                cid,
                listName,
                listKind,
                note,
                tags,
                override,
                titleCoords,
                savedAt,
                visitStatus,
                visitSource,
                visitedAt);
    }

    ImportedPlaceUpsertCommand toUpsertCommand(
            GeoPoint coordinates,
            CoordSource coordSource,
            @Nullable String countryCode,
            @Nullable String cityName) {
        return new ImportedPlaceUpsertCommand(
                canonicalTitle,
                coordinates,
                coordSource,
                null,
                cid,
                classification.category(),
                classification.subcategory(),
                classification.source(),
                countryCode,
                cityName,
                accountLabel,
                listName,
                listKind,
                note,
                tags,
                url,
                savedAt,
                visitStatus,
                visitSource,
                visitedAt);
    }

    AttachCsvMembershipCommand toAttachCommand(long googleCid) {
        return new AttachCsvMembershipCommand(
                googleCid,
                canonicalTitle,
                titleCoords,
                classification.category(),
                classification.subcategory(),
                classification.source(),
                accountLabel,
                listName,
                listKind,
                note,
                tags,
                url,
                savedAt,
                visitStatus,
                visitSource,
                visitedAt);
    }
}
