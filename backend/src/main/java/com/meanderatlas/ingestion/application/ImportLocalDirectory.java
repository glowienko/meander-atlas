package com.meanderatlas.ingestion.application;

import com.meanderatlas.atlas.AtlasFacade;
import com.meanderatlas.atlas.TimelineReconciliationOutcome;
import com.meanderatlas.atlas.TimelineVisitSignal;
import com.meanderatlas.atlas.UpsertedPlaceView;
import com.meanderatlas.ingestion.AccountImportSummaryView;
import com.meanderatlas.ingestion.LocalImportSummaryView;
import com.meanderatlas.ingestion.application.ImportedPlaceResolver.Resolution;
import com.meanderatlas.ingestion.domain.TextNormalizer;
import com.meanderatlas.ingestion.domain.source.AccountImportSource;
import com.meanderatlas.ingestion.domain.source.DiscoveredImportFile;
import com.meanderatlas.ingestion.domain.source.EnrichmentJobRepository;
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
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ImportLocalDirectory {

    private static final Logger LOG = LoggerFactory.getLogger(ImportLocalDirectory.class);
    private static final String SOURCE_TYPE_LOCAL = "LOCAL_DIRECTORY";
    private static final String JSON_EXTENSION = ".json";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final TripImportSourcePort importSourcePort;
    private final ImportRepository importRepository;
    private final EnrichmentJobRepository enrichmentJobRepository;
    private final AtlasFacade atlasFacade;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionOperations transactionOperations;
    private final ImportedPlaceResolver resolver;

    public ImportLocalDirectory(
            TripImportSourcePort importSourcePort,
            ImportRepository importRepository,
            EnrichmentJobRepository enrichmentJobRepository,
            AtlasFacade atlasFacade,
            CurrentUserProvider currentUserProvider,
            TransactionOperations transactionOperations) {
        this.importSourcePort = importSourcePort;
        this.importRepository = importRepository;
        this.enrichmentJobRepository = enrichmentJobRepository;
        this.atlasFacade = atlasFacade;
        this.currentUserProvider = currentUserProvider;
        this.transactionOperations = transactionOperations;
        this.resolver = new ImportedPlaceResolver(atlasFacade);
    }

    public LocalImportSummaryView execute(@Nullable String accountFilter, boolean force) {
        UserId userId = currentUserProvider.currentUserId();
        List<AccountImportSource> accounts = importSourcePort.discoverAccounts(accountFilter);
        List<PreparedAccount> prepared =
                accounts.stream().map(account -> prepareAccount(userId, account, force)).toList();

        List<PreparedAccount> activeAccounts =
                prepared.stream().filter(item -> !item.skipped()).toList();
        Map<String, AccountImportSummaryView> importedByAccount =
                activeAccounts.isEmpty()
                        ? Map.of()
                        : Objects.requireNonNull(
                                transactionOperations.execute(
                                        _ -> executeTwoPassImport(userId, activeAccounts)));

        List<AccountImportSummaryView> accountSummaries =
                prepared.stream()
                        .map(item ->
                                item.skipped()
                                        ? AccountImportSummaryView.skipped(
                                                item.source().accountLabel(),
                                                item.source().contentSha256(),
                                                item.source().files().size())
                                        : Objects.requireNonNull(
                                                importedByAccount.get(
                                                        item.source().accountLabel())))
                        .toList();

        LocalImportSummaryView summary = LocalImportSummaryView.fromAccounts(accountSummaries);
        LOG.info(
                "Local import finished (accounts={}, created={}, merged={}, csvCidMatched={}, queuedLookup={}, timelineMatched={}, timelineTriage={}, timelineUnmatched={})",
                summary.accounts().size(),
                summary.totalPlacesCreated(),
                summary.totalPlacesMerged(),
                summary.totalCsvCidMatched(),
                summary.totalQueuedResolutionJobs(),
                summary.totalTimelineMatchedVisited(),
                summary.totalTimelineMarkedTriage(),
                summary.totalTimelineUnmatched());
        return summary;
    }

    private PreparedAccount prepareAccount(
            UserId userId, AccountImportSource account, boolean force) {
        if (!force
                && importRepository.hasCompletedBatch(
                        userId, account.accountLabel(), account.contentSha256())) {
            return new PreparedAccount(account, true, null);
        }
        return new PreparedAccount(account, false, parseAccountFiles(account.files()));
    }

    private Map<String, AccountImportSummaryView> executeTwoPassImport(
            UserId userId, List<PreparedAccount> activeAccounts) {
        List<Pass1AccountState> pass1States =
                activeAccounts.stream().map(item -> runPass1(userId, item)).toList();

        List<Pass2AccountState> pass2States =
                pass1States.stream().map(state -> runPass2ReconcileLinks(userId, state)).toList();

        List<PlaceId> touchedPlaceIds =
                Stream.concat(
                                pass1States.stream()
                                        .flatMap(state -> state.touchedPlaceIds().stream()),
                                pass2States.stream()
                                        .flatMap(state -> state.touchedPlaceIds().stream()))
                        .distinct()
                        .toList();
        enrichmentJobRepository.enqueueOpenCatalogEnrichments(
                userId, atlasFacade.findPlaceIdsNeedingEnrichment(touchedPlaceIds));

        return pass2States.stream()
                .map(this::runPass2TimelineAndFinalize)
                .collect(
                        Collectors.toUnmodifiableMap(
                                AccountImportSummaryView::accountLabel, Function.identity()));
    }

    private Pass1AccountState runPass1(UserId userId, PreparedAccount item) {
        AccountImportSource account = item.source();
        ParsedAccountData parsed = Objects.requireNonNull(item.parsed());
        UUID batchId =
                importRepository.startOrRestartBatch(
                        userId, account.accountLabel(), account.contentSha256(), SOURCE_TYPE_LOCAL);
        importRepository.insertRecords(batchId, userId, account.accountLabel(), parsed.records());

        List<UpsertedPlaceView> savedPlaceResults =
                parsed.savedPlaces().stream()
                        .map(entry ->
                                upsertTakeoutPlace(
                                        savedDraft(account.accountLabel(), entry),
                                        entry.place().coordinates(),
                                        entry.place().countryCode()))
                        .toList();
        List<UpsertedPlaceView> geocodedReviewResults =
                parsed.reviews().stream()
                        .map(ReviewEntry::review)
                        .filter(review -> review.coordinates() != null)
                        .map(review ->
                                upsertTakeoutPlace(
                                        reviewDraft(account.accountLabel(), review),
                                        Objects.requireNonNull(review.coordinates()),
                                        review.countryCode()))
                        .toList();

        int totalUpserted = savedPlaceResults.size() + geocodedReviewResults.size();
        int placesCreated =
                (int)
                        Stream.concat(savedPlaceResults.stream(), geocodedReviewResults.stream())
                                .filter(UpsertedPlaceView::created)
                                .count();
        int placesMerged = totalUpserted - placesCreated;
        List<PlaceId> touchedPlaceIds =
                Stream.concat(savedPlaceResults.stream(), geocodedReviewResults.stream())
                        .map(UpsertedPlaceView::placeId)
                        .toList();
        return new Pass1AccountState(
                account, parsed, batchId, placesCreated, placesMerged, touchedPlaceIds);
    }

    private Pass2AccountState runPass2ReconcileLinks(UserId userId, Pass1AccountState state) {
        String accountLabel = state.account().accountLabel();
        ParsedAccountData parsed = state.parsed();

        List<ReconciledRecord> coordlessReviewOutcomes =
                parsed.reviews().stream()
                        .filter(entry -> entry.review().coordinates() == null)
                        .map(entry ->
                                new ReconciledRecord(
                                        entry.recordId(),
                                        resolver.attachByCid(
                                                        reviewDraft(accountLabel, entry.review()))
                                                .orElse(null)))
                        .toList();
        List<ReconciledRecord> csvOutcomes =
                parsed.csvRows().stream()
                        .map(entry ->
                                new ReconciledRecord(
                                        entry.recordId(),
                                        resolver.resolve(csvDraft(accountLabel, entry.row()))
                                                .orElse(null)))
                        .toList();
        List<ReconciledRecord> allOutcomes =
                Stream.concat(coordlessReviewOutcomes.stream(), csvOutcomes.stream()).toList();

        List<NewResolutionJob> pendingJobs =
                allOutcomes.stream()
                        .filter(outcome -> outcome.resolution() == null)
                        .map(outcome -> new NewResolutionJob(UUID.randomUUID(), outcome.recordId()))
                        .toList();
        importRepository.enqueueCoordinateLookups(userId, pendingJobs);

        List<PlaceId> touchedPlaceIds =
                allOutcomes.stream()
                        .map(ReconciledRecord::resolution)
                        .filter(Objects::nonNull)
                        .map(Resolution::placeId)
                        .toList();
        return new Pass2AccountState(
                state,
                count(coordlessReviewOutcomes, Resolution.CidMatched.class::isInstance),
                count(csvOutcomes, Resolution.CidMatched.class::isInstance),
                count(
                        csvOutcomes,
                        resolution ->
                                resolution instanceof Resolution.Materialized materialized
                                        && materialized.place().created()),
                count(
                        csvOutcomes,
                        resolution ->
                                resolution instanceof Resolution.Materialized materialized
                                        && !materialized.place().created()),
                pendingJobs.size(),
                touchedPlaceIds);
    }

    private static int count(List<ReconciledRecord> outcomes, Predicate<Resolution> predicate) {
        return (int)
                outcomes.stream()
                        .map(ReconciledRecord::resolution)
                        .filter(Objects::nonNull)
                        .filter(predicate)
                        .count();
    }

    private AccountImportSummaryView runPass2TimelineAndFinalize(Pass2AccountState state) {
        Pass1AccountState pass1 = state.pass1();
        AccountImportSource account = pass1.account();
        ParsedAccountData parsed = pass1.parsed();

        List<TimelineReconciliationOutcome> timelineOutcomes =
                parsed.timelineVisits().stream()
                        .sorted(
                                Comparator.comparing(
                                        ParsedTimelineVisit::startTime,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(visit ->
                                atlasFacade.reconcileTimelineVisit(
                                        new TimelineVisitSignal(
                                                visit.googlePlaceId(),
                                                visit.coordinates(),
                                                visit.startTime())))
                        .toList();

        importRepository.markBatchCompleted(pass1.batchId(), parsed.records().size());

        return new AccountImportSummaryView(
                account.accountLabel(),
                account.contentSha256(),
                false,
                account.files().size(),
                parsed.records().size(),
                pass1.placesCreated() + state.csvPlacesCreated(),
                pass1.placesMerged() + state.coordlessReviewsMatched() + state.csvPlacesMerged(),
                state.csvCidMatched(),
                state.queuedResolutionJobs(),
                countOutcome(timelineOutcomes, TimelineReconciliationOutcome.MATCHED_VISITED),
                countOutcome(timelineOutcomes, TimelineReconciliationOutcome.MARKED_NEEDS_TRIAGE),
                countOutcome(timelineOutcomes, TimelineReconciliationOutcome.UNMATCHED));
    }

    private UpsertedPlaceView upsertTakeoutPlace(
            ImportedPlaceDraft draft, GeoPoint coordinates, @Nullable String countryCode) {
        return atlasFacade.upsertImportedPlace(
                draft.toUpsertCommand(coordinates, CoordSource.TAKEOUT, countryCode, null));
    }

    private static ImportedPlaceDraft savedDraft(String accountLabel, SavedPlaceEntry entry) {
        ParsedSavedPlace place = entry.place();
        return ImportedPlaceDraft.saved(
                accountLabel,
                place.name(),
                place.googleMapsUrl(),
                ImportedPlaceDraft.cidBits(place.googleCid()),
                entry.listName(),
                place.listKind(),
                place.comment(),
                null,
                place.savedAt());
    }

    private static ImportedPlaceDraft csvDraft(String accountLabel, ParsedSavedCsvRow row) {
        return ImportedPlaceDraft.saved(
                accountLabel,
                row.title(),
                row.url(),
                ImportedPlaceDraft.cidBits(row.googleCid()),
                row.listName(),
                row.listKind(),
                row.note(),
                row.tags(),
                null);
    }

    private static ImportedPlaceDraft reviewDraft(String accountLabel, ParsedReview review) {
        return ImportedPlaceDraft.review(
                accountLabel,
                review.name(),
                review.reviewText(),
                review.googleMapsUrl(),
                ImportedPlaceDraft.cidBits(review.googleCid()),
                review.reviewedAt());
    }

    private static ParsedAccountData parseAccountFiles(List<DiscoveredImportFile> files) {
        Stream.Builder<NewImportRecord> records = Stream.builder();
        Stream.Builder<SavedPlaceEntry> savedPlaces = Stream.builder();
        Stream.Builder<ReviewEntry> reviews = Stream.builder();
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
            Stream.Builder<ReviewEntry> reviews,
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
                                    NewImportRecord record =
                                            toReviewRecord(file.relativePath(), review);
                                    records.add(record);
                                    reviews.add(new ReviewEntry(record.id(), review));
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
        return NewImportRecord.starredGeoJson(
                sourceFile,
                place.name(),
                place.googleMapsUrl(),
                ImportedPlaceDraft.cidBits(place.googleCid()),
                place.coordinates(),
                place.savedAt(),
                toJson(place));
    }

    private static NewImportRecord toReviewRecord(String sourceFile, ParsedReview review) {
        return NewImportRecord.reviewGeoJson(
                sourceFile,
                review.name(),
                review.googleMapsUrl(),
                ImportedPlaceDraft.cidBits(review.googleCid()),
                review.coordinates(),
                review.reviewedAt(),
                toJson(review));
    }

    private static NewImportRecord toCsvRecord(String sourceFile, ParsedSavedCsvRow row) {
        return NewImportRecord.savedCsv(
                sourceFile,
                row.title(),
                row.url(),
                ImportedPlaceDraft.cidBits(row.googleCid()),
                toJson(row));
    }

    private static NewImportRecord toTimelineRecord(String sourceFile, ParsedTimelineVisit visit) {
        return NewImportRecord.timelineVisit(
                sourceFile,
                visit.googlePlaceId(),
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
            return ImportedPlaceDraft.LIST_NAME_SAVED_DEFAULT;
        }
        String raw = fileName.toString();
        String trimmed =
                raw.toLowerCase(Locale.ROOT).endsWith(JSON_EXTENSION)
                        ? raw.substring(0, raw.length() - JSON_EXTENSION.length())
                        : raw;
        String normalized = TextNormalizer.trimToNull(trimmed);
        return normalized != null ? normalized : ImportedPlaceDraft.LIST_NAME_SAVED_DEFAULT;
    }

    private static int countOutcome(
            List<TimelineReconciliationOutcome> outcomes, TimelineReconciliationOutcome target) {
        return (int) outcomes.stream().filter(outcome -> outcome == target).count();
    }

    private record PreparedAccount(
            AccountImportSource source, boolean skipped, @Nullable ParsedAccountData parsed) {}

    private record Pass1AccountState(
            AccountImportSource account,
            ParsedAccountData parsed,
            UUID batchId,
            int placesCreated,
            int placesMerged,
            List<PlaceId> touchedPlaceIds) {}

    private record Pass2AccountState(
            Pass1AccountState pass1,
            int coordlessReviewsMatched,
            int csvCidMatched,
            int csvPlacesCreated,
            int csvPlacesMerged,
            int queuedResolutionJobs,
            List<PlaceId> touchedPlaceIds) {}

    private record SavedPlaceEntry(String listName, ParsedSavedPlace place) {}

    private record ReviewEntry(UUID recordId, ParsedReview review) {}

    private record CsvRowEntry(UUID recordId, ParsedSavedCsvRow row) {}

    private record ParsedAccountData(
            List<NewImportRecord> records,
            List<SavedPlaceEntry> savedPlaces,
            List<ReviewEntry> reviews,
            List<CsvRowEntry> csvRows,
            List<ParsedTimelineVisit> timelineVisits) {}

    private record ReconciledRecord(UUID recordId, @Nullable Resolution resolution) {}
}
