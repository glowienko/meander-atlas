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
        String payloadJson) {}
