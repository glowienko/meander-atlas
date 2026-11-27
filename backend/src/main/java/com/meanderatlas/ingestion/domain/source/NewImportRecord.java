package com.meanderatlas.ingestion.domain.source;

import com.meanderatlas.shared.GeoPoint;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record NewImportRecord(
        UUID id,
        ImportRecordKind recordType,
        String sourceFile,
        @Nullable String rawTitle,
        @Nullable String rawUrl,
        @Nullable String googlePlaceId,
        @Nullable Long googleCid,
        @Nullable GeoPoint coordinates,
        @Nullable Instant observedAt,
        String payloadJson) {

    public static NewImportRecord starredGeoJson(
            String sourceFile,
            @Nullable String rawTitle,
            @Nullable String rawUrl,
            @Nullable Long googleCid,
            @Nullable GeoPoint coordinates,
            @Nullable Instant observedAt,
            String payloadJson) {
        return new NewImportRecord(
                UUID.randomUUID(),
                ImportRecordKind.STARRED_GEOJSON,
                sourceFile,
                rawTitle,
                rawUrl,
                null,
                googleCid,
                coordinates,
                observedAt,
                payloadJson);
    }

    public static NewImportRecord reviewGeoJson(
            String sourceFile,
            @Nullable String rawTitle,
            @Nullable String rawUrl,
            @Nullable Long googleCid,
            @Nullable GeoPoint coordinates,
            @Nullable Instant observedAt,
            String payloadJson) {
        return new NewImportRecord(
                UUID.randomUUID(),
                ImportRecordKind.REVIEW_GEOJSON,
                sourceFile,
                rawTitle,
                rawUrl,
                null,
                googleCid,
                coordinates,
                observedAt,
                payloadJson);
    }

    public static NewImportRecord savedCsv(
            String sourceFile,
            @Nullable String rawTitle,
            @Nullable String rawUrl,
            @Nullable Long googleCid,
            String payloadJson) {
        return new NewImportRecord(
                UUID.randomUUID(),
                ImportRecordKind.SAVED_CSV,
                sourceFile,
                rawTitle,
                rawUrl,
                null,
                googleCid,
                null,
                null,
                payloadJson);
    }

    public static NewImportRecord timelineVisit(
            String sourceFile,
            @Nullable String googlePlaceId,
            @Nullable GeoPoint coordinates,
            @Nullable Instant observedAt,
            String payloadJson) {
        return new NewImportRecord(
                UUID.randomUUID(),
                ImportRecordKind.TIMELINE_VISIT,
                sourceFile,
                null,
                null,
                googlePlaceId,
                null,
                coordinates,
                observedAt,
                payloadJson);
    }
}
