package com.meanderatlas.ingestion.application;

import com.meanderatlas.atlas.AtlasFacade;
import com.meanderatlas.atlas.AttachCsvMembershipCommand;
import com.meanderatlas.atlas.ImportedPlaceUpsertCommand;
import com.meanderatlas.atlas.TimelineReconciliationOutcome;
import com.meanderatlas.atlas.TimelineVisitSignal;
import com.meanderatlas.atlas.UpsertedPlaceView;
import com.meanderatlas.ingestion.AccountImportSummaryView;
import com.meanderatlas.ingestion.LocalImportSummaryView;
import com.meanderatlas.ingestion.domain.GoogleCid;
import com.meanderatlas.ingestion.domain.TextNormalizer;
import com.meanderatlas.ingestion.domain.classification.PlaceCategoryClassifier;
import com.meanderatlas.ingestion.domain.classification.PlaceClassification;
import com.meanderatlas.ingestion.domain.source.AccountImportSource;
import com.meanderatlas.ingestion.domain.source.DiscoveredImportFile;
import com.meanderatlas.ingestion.domain.source.ImportRecordKind;
import com.meanderatlas.ingestion.domain.source.ImportRepository;
import com.meanderatlas.ingestion.domain.source.NewImportRecord;
import com.meanderatlas.ingestion.domain.source.NewResolutionJob;
import com.meanderatlas.ingestion.domain.source.TripImportSourcePort;
import com.meanderatlas.ingestion.domain.takeout.ParsedReview;
import com.meanderatlas.ingestion.domain.takeout.ParsedSavedCsvRow;
import com.meanderatlas.ingestion.domain.takeout.ParsedSavedPlace;
import com.meanderatlas.ingestion.domain.takeout.TakeoutCsvParser;
import com.meanderatlas.ingestion.domain.takeout.TakeoutGeoJsonParser;
import com.meanderatlas.ingestion.domain.timeline.ParsedTimelineVisit;
import com.meanderatlas.ingestion.domain.timeline.TimelineParser;
import com.meanderatlas.shared.CoordSource;
import com.meanderatlas.shared.CurrentUserProvider;
import com.meanderatlas.shared.GeoPoint;
import com.meanderatlas.shared.ListKind;
import com.meanderatlas.shared.UserId;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ImportLocalDirectory {

    private static final String SOURCE_TYPE_LOCAL = "LOCAL_DIRECTORY";
    private static final String LIST_NAME_SAVED_DEFAULT = "Saved Places";
    private static final String LIST_NAME_REVIEWS = "Google Reviews";
    private static final String FALLBACK_REVIEW_NAME = "Reviewed place";
    private static final String JSON_EXTENSION = ".json";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final TripImportSourcePort importSourcePort;
    private final ImportRepository importRepository;
    private final AtlasFacade atlasFacade;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionTemplate transactionTemplate;

    public ImportLocalDirectory(
            TripImportSourcePort importSourcePort,
            ImportRepository importRepository,
            AtlasFacade atlasFacade,
            CurrentUserProvider currentUserProvider,
            TransactionTemplate transactionTemplate) {
        this.importSourcePort = importSourcePort;
        this.importRepository = importRepository;
        this.atlasFacade = atlasFacade;
        this.currentUserProvider = currentUserProvider;
        this.transactionTemplate = transactionTemplate;
    }

    public LocalImportSummaryView execute(@Nullable String accountFilter, boolean force) {
        UserId userId = currentUserProvider.currentUserId();
        List<AccountImportSource> accounts = importSourcePort.discoverAccounts(accountFilter);
        List<AccountImportSummaryView> accountSummaries =
                accounts.stream().map(account -> importAccount(userId, account, force)).toList();
        return new LocalImportSummaryView(
                accountSummaries,
                sumInt(accountSummaries.stream().mapToInt(AccountImportSummaryView::placesCreated)),
                sumInt(accountSummaries.stream().mapToInt(AccountImportSummaryView::placesMerged)),
                sumInt(accountSummaries.stream().mapToInt(AccountImportSummaryView::csvCidMatched)),
                sumInt(
                        accountSummaries.stream()
                                .mapToInt(AccountImportSummaryView::queuedResolutionJobs)),
                sumInt(
                        accountSummaries.stream()
                                .mapToInt(AccountImportSummaryView::timelineMatchedVisited)),
                sumInt(
                        accountSummaries.stream()
                                .mapToInt(AccountImportSummaryView::timelineMarkedTriage)),
                sumInt(
                        accountSummaries.stream()
                                .mapToInt(AccountImportSummaryView::timelineUnmatched)));
    }

    private AccountImportSummaryView importAccount(
            UserId userId, AccountImportSource account, boolean force) {
        if (!force
                && importRepository.hasCompletedBatch(
                        userId, account.accountLabel(), account.contentSha256())) {
            return skippedSummary(account);
        }

        ParsedAccountData parsed = parseAccountFiles(account.files());
        return Objects.requireNonNull(
                transactionTemplate.execute(
                        _ -> persistAndReconcileAccount(userId, account, parsed)));
    }

    private AccountImportSummaryView persistAndReconcileAccount(
            UserId userId, AccountImportSource account, ParsedAccountData parsed) {
        UUID batchId =
                importRepository.startOrRestartBatch(
                        userId, account.accountLabel(), account.contentSha256(), SOURCE_TYPE_LOCAL);
        importRepository.insertRecords(batchId, userId, account.accountLabel(), parsed.records());

        List<UpsertedPlaceView> savedPlaceResults =
                parsed.savedPlaces().stream()
                        .map(entry -> upsertSavedPlace(account.accountLabel(), entry))
                        .toList();
        List<UpsertedPlaceView> reviewResults =
                parsed.reviews().stream()
                        .filter(review -> review.coordinates() != null)
                        .map(review -> upsertReview(account.accountLabel(), review))
                        .toList();

        List<CsvMatchOutcome> csvOutcomes =
                parsed.csvRows().stream()
                        .map(entry -> reconcileCsvRow(account.accountLabel(), entry))
                        .toList();
        List<NewResolutionJob> pendingJobs =
                csvOutcomes.stream()
                        .map(CsvMatchOutcome::pendingJob)
                        .filter(Objects::nonNull)
                        .toList();
        importRepository.enqueueCoordinateLookups(userId, pendingJobs);

        List<TimelineReconciliationOutcome> timelineOutcomes =
                parsed.timelineVisits().stream()
                        .map(visit ->
                                atlasFacade.reconcileTimelineVisit(
                                        new TimelineVisitSignal(
                                                visit.googlePlaceId(),
                                                visit.coordinates(),
                                                visit.startTime())))
                        .toList();

        importRepository.markBatchCompleted(batchId, parsed.records().size());

        int totalUpserted = savedPlaceResults.size() + reviewResults.size();
        int placesCreated =
                (int)
                        Stream.concat(savedPlaceResults.stream(), reviewResults.stream())
                                .filter(UpsertedPlaceView::created)
                                .count();
        int placesMerged = totalUpserted - placesCreated;
        int csvCidMatched = (int) csvOutcomes.stream().filter(CsvMatchOutcome::cidMatched).count();
        int timelineMatchedVisited =
                countOutcome(timelineOutcomes, TimelineReconciliationOutcome.MATCHED_VISITED);
        int timelineMarkedTriage =
                countOutcome(timelineOutcomes, TimelineReconciliationOutcome.MARKED_NEEDS_TRIAGE);
        int timelineUnmatched =
                countOutcome(timelineOutcomes, TimelineReconciliationOutcome.UNMATCHED);

        return new AccountImportSummaryView(
                account.accountLabel(),
                account.contentSha256(),
                false,
                account.files().size(),
                parsed.records().size(),
                placesCreated,
                placesMerged,
                csvCidMatched,
                pendingJobs.size(),
                timelineMatchedVisited,
                timelineMarkedTriage,
                timelineUnmatched);
    }

    private UpsertedPlaceView upsertSavedPlace(String accountLabel, SavedPlaceEntry entry) {
        ParsedSavedPlace place = entry.place();
        PlaceClassification classification =
                PlaceCategoryClassifier.classify(place.name(), place.comment(), null);
        Long cid = place.googleCid() == null ? null : place.googleCid().unsignedBits();
        return atlasFacade.upsertImportedPlace(
                new ImportedPlaceUpsertCommand(
                        place.name(),
                        place.coordinates(),
                        CoordSource.TAKEOUT,
                        null,
                        cid,
                        classification.category(),
                        classification.subcategory(),
                        classification.source(),
                        place.countryCode(),
                        null,
                        accountLabel,
                        entry.listName(),
                        place.listKind(),
                        place.comment(),
                        null,
                        place.googleMapsUrl(),
                        place.savedAt(),
                        null,
                        null,
                        null));
    }

    private UpsertedPlaceView upsertReview(String accountLabel, ParsedReview review) {
        GeoPoint coordinates = Objects.requireNonNull(review.coordinates());
        String trimmedName = TextNormalizer.trimToNull(review.name());
        String canonicalName = trimmedName != null ? trimmedName : FALLBACK_REVIEW_NAME;
        PlaceClassification classification =
                PlaceCategoryClassifier.classify(canonicalName, review.reviewText(), null);
        Long cid = review.googleCid() == null ? null : review.googleCid().unsignedBits();
        return atlasFacade.upsertImportedPlace(
                new ImportedPlaceUpsertCommand(
                        canonicalName,
                        coordinates,
                        CoordSource.TAKEOUT,
                        null,
                        cid,
                        classification.category(),
                        classification.subcategory(),
                        classification.source(),
                        review.countryCode(),
                        null,
                        accountLabel,
                        LIST_NAME_REVIEWS,
                        ListKind.REVIEW,
                        review.reviewText(),
                        null,
                        review.googleMapsUrl(),
                        review.reviewedAt(),
                        VisitStatus.VISITED,
                        VisitSource.REVIEW,
                        review.reviewedAt()));
    }

    private CsvMatchOutcome reconcileCsvRow(String accountLabel, CsvRowEntry entry) {
        ParsedSavedCsvRow row = entry.row();
        GoogleCid cid = row.googleCid();
        if (cid == null) {
            return new CsvMatchOutcome(
                    new NewResolutionJob(UUID.randomUUID(), entry.recordId()), false);
        }
        PlaceClassification classification =
                PlaceCategoryClassifier.classify(row.title(), row.note(), row.tags());
        boolean matched =
                atlasFacade.attachCsvMembershipByCid(
                        new AttachCsvMembershipCommand(
                                cid.unsignedBits(),
                                row.title(),
                                classification.category(),
                                classification.subcategory(),
                                classification.source(),
                                accountLabel,
                                row.listName(),
                                row.listKind(),
                                row.note(),
                                row.tags(),
                                row.url()));
        if (matched) {
            return new CsvMatchOutcome(null, true);
        }
        return new CsvMatchOutcome(
                new NewResolutionJob(UUID.randomUUID(), entry.recordId()), false);
    }

    private static ParsedAccountData parseAccountFiles(List<DiscoveredImportFile> files) {
        Stream.Builder<NewImportRecord> records = Stream.builder();
        Stream.Builder<SavedPlaceEntry> savedPlaces = Stream.builder();
        Stream.Builder<ParsedReview> reviews = Stream.builder();
        Stream.Builder<CsvRowEntry> csvRows = Stream.builder();
        Stream.Builder<ParsedTimelineVisit> timelineVisits = Stream.builder();

        files.forEach(file ->
                parseSingleFile(file, records, savedPlaces, reviews, csvRows, timelineVisits));

        return new ParsedAccountData(
                records.build().toList(),
                savedPlaces.build().toList(),
                reviews.build().toList(),
                csvRows.build().toList(),
                timelineVisits.build().toList());
    }

    private static void parseSingleFile(
            DiscoveredImportFile file,
            Stream.Builder<NewImportRecord> records,
            Stream.Builder<SavedPlaceEntry> savedPlaces,
            Stream.Builder<ParsedReview> reviews,
            Stream.Builder<CsvRowEntry> csvRows,
            Stream.Builder<ParsedTimelineVisit> timelineVisits) {
        try {
            switch (file.kind()) {
                case STARRED_GEOJSON -> {
                    String listName = deriveListName(file.absolutePath());
                    TakeoutGeoJsonParser.parseSavedPlaces(file.absolutePath())
                            .forEach(place -> {
                                records.add(toSavedPlaceRecord(file.relativePath(), place));
                                savedPlaces.add(new SavedPlaceEntry(listName, place));
                            });
                }
                case REVIEW_GEOJSON ->
                        TakeoutGeoJsonParser.parseReviews(file.absolutePath())
                                .forEach(review -> {
                                    records.add(toReviewRecord(file.relativePath(), review));
                                    reviews.add(review);
                                });
                case SAVED_CSV ->
                        TakeoutCsvParser.parse(file.absolutePath())
                                .forEach(row -> {
                                    NewImportRecord record = toCsvRecord(file.relativePath(), row);
                                    records.add(record);
                                    csvRows.add(new CsvRowEntry(record.id(), row));
                                });
                case TIMELINE_JSON ->
                        TimelineParser.parse(file.absolutePath())
                                .forEach(visit -> {
                                    records.add(toTimelineRecord(file.relativePath(), visit));
                                    timelineVisits.add(visit);
                                });
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(
                    "Failed to parse import file: " + file.absolutePath(), ex);
        }
    }

    private static NewImportRecord toSavedPlaceRecord(String sourceFile, ParsedSavedPlace place) {
        Long cid = place.googleCid() == null ? null : place.googleCid().unsignedBits();
        return new NewImportRecord(
                UUID.randomUUID(),
                ImportRecordKind.STARRED_GEOJSON,
                sourceFile,
                place.name(),
                place.googleMapsUrl(),
                null,
                cid,
                place.coordinates(),
                place.savedAt(),
                toJson(place));
    }

    private static NewImportRecord toReviewRecord(String sourceFile, ParsedReview review) {
        Long cid = review.googleCid() == null ? null : review.googleCid().unsignedBits();
        return new NewImportRecord(
                UUID.randomUUID(),
                ImportRecordKind.REVIEW_GEOJSON,
                sourceFile,
                review.name(),
                review.googleMapsUrl(),
                null,
                cid,
                review.coordinates(),
                review.reviewedAt(),
                toJson(review));
    }

    private static NewImportRecord toCsvRecord(String sourceFile, ParsedSavedCsvRow row) {
        Long cid = row.googleCid() == null ? null : row.googleCid().unsignedBits();
        return new NewImportRecord(
                UUID.randomUUID(),
                ImportRecordKind.SAVED_CSV,
                sourceFile,
                row.title(),
                row.url(),
                null,
                cid,
                null,
                null,
                toJson(row));
    }

    private static NewImportRecord toTimelineRecord(String sourceFile, ParsedTimelineVisit visit) {
        return new NewImportRecord(
                UUID.randomUUID(),
                ImportRecordKind.TIMELINE_VISIT,
                sourceFile,
                null,
                null,
                visit.googlePlaceId(),
                null,
                visit.coordinates(),
                visit.startTime(),
                toJson(visit));
    }

    private static String toJson(Object value) {
        return JSON_MAPPER.writeValueAsString(value);
    }

    private static String deriveListName(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return LIST_NAME_SAVED_DEFAULT;
        }
        String raw = fileName.toString();
        String trimmed =
                raw.toLowerCase(java.util.Locale.ROOT).endsWith(JSON_EXTENSION)
                        ? raw.substring(0, raw.length() - JSON_EXTENSION.length())
                        : raw;
        String normalized = TextNormalizer.trimToNull(trimmed);
        return normalized != null ? normalized : LIST_NAME_SAVED_DEFAULT;
    }

    private static AccountImportSummaryView skippedSummary(AccountImportSource account) {
        return new AccountImportSummaryView(
                account.accountLabel(),
                account.contentSha256(),
                true,
                account.files().size(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0);
    }

    private static int countOutcome(
            List<TimelineReconciliationOutcome> outcomes, TimelineReconciliationOutcome target) {
        return (int) outcomes.stream().filter(outcome -> outcome == target).count();
    }

    private static int sumInt(java.util.stream.IntStream stream) {
        return stream.sum();
    }

    private record SavedPlaceEntry(String listName, ParsedSavedPlace place) {}

    private record CsvRowEntry(UUID recordId, ParsedSavedCsvRow row) {}

    private record ParsedAccountData(
            List<NewImportRecord> records,
            List<SavedPlaceEntry> savedPlaces,
            List<ParsedReview> reviews,
            List<CsvRowEntry> csvRows,
            List<ParsedTimelineVisit> timelineVisits) {}

    private record CsvMatchOutcome(@Nullable NewResolutionJob pendingJob, boolean cidMatched) {}
}
