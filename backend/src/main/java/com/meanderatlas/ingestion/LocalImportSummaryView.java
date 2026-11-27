package com.meanderatlas.ingestion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.function.ToIntFunction;

public record LocalImportSummaryView(
        @NotNull List<AccountImportSummaryView> accounts,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPlacesCreated,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPlacesMerged,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalCsvCidMatched,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalQueuedResolutionJobs,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalTimelineMatchedVisited,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalTimelineMarkedTriage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalTimelineUnmatched) {

    public static LocalImportSummaryView fromAccounts(List<AccountImportSummaryView> accounts) {
        return new LocalImportSummaryView(
                accounts,
                sum(accounts, AccountImportSummaryView::placesCreated),
                sum(accounts, AccountImportSummaryView::placesMerged),
                sum(accounts, AccountImportSummaryView::csvCidMatched),
                sum(accounts, AccountImportSummaryView::queuedResolutionJobs),
                sum(accounts, AccountImportSummaryView::timelineMatchedVisited),
                sum(accounts, AccountImportSummaryView::timelineMarkedTriage),
                sum(accounts, AccountImportSummaryView::timelineUnmatched));
    }

    private static int sum(
            List<AccountImportSummaryView> accounts,
            ToIntFunction<AccountImportSummaryView> metric) {
        return accounts.stream().mapToInt(metric).sum();
    }
}
