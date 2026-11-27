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
import com.meanderatlas.ingestion.domain.GoogleS2CellDecoder;
import com.meanderatlas.ingestion.domain.TextNormalizer;
import com.meanderatlas.ingestion.domain.TitleCoordinateParser;
import com.meanderatlas.ingestion.domain.classification.PlaceCategoryClassifier;
import com.meanderatlas.ingestion.domain.classification.PlaceClassification;
import com.meanderatlas.ingestion.domain.source.AccountImportSource;
import com.meanderatlas.ingestion.domain.source.DiscoveredImportFile;
import com.meanderatlas.ingestion.domain.source.EnrichmentJobRepository;
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
import com.meanderatlas.shared.PlaceId;
import com.meanderatlas.shared.UserId;
import com.meanderatlas.shared.VisitSource;
import com.meanderatlas.shared.VisitStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
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
    private final EnrichmentJobRepository enrichmentJobRepository;
    private final AtlasFacade atlasFacade;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionOperations transactionOperations;

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
                                        ? skippedSummary(item.source())
                                        : Objects.requireNonNull(
                                                importedByAccount.get(
                                                        item.source().accountLabel())))
                        .toList();

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
        if (!touchedPlaceIds.isEmpty()) {
            Set<PlaceId> needingEnrichment =
                    Set.copyOf(atlasFacade.findPlaceIdsNeedingEnrichment());
            List<PlaceId> toEnqueue =
                    touchedPlaceIds.stream().filter(needingEnrichment::contains).toList();
            enrichmentJobRepository.enqueueOpenCatalogEnrichments(userId, toEnqueue);
        }

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
                        .map(entry -> upsertSavedPlace(account.accountLabel(), entry))
                        .toList();
        List<UpsertedPlaceView> geocodedReviewResults =
                parsed.reviews().stream()
                        .filter(entry -> entry.review().coordinates() != null)
                        .map(entry -> upsertReview(account.accountLabel(), entry.review()))
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

        List<MatchOutcome> coordlessReviewOutcomes =
                parsed.reviews().stream()
                        .filter(entry -> entry.review().coordinates() == null)
                        .map(entry -> reconcileCoordlessReview(accountLabel, entry))
                        .toList();
        List<MatchOutcome> csvOutcomes =
                parsed.csvRows().stream()
                        .map(entry -> reconcileCsvRow(accountLabel, entry))
                        .toList();

        List<NewResolutionJob> pendingJobs =
                Stream.concat(coordlessReviewOutcomes.stream(), csvOutcomes.stream())
                        .map(MatchOutcome::pendingJob)
                        .filter(Objects::nonNull)
                        .toList();
        importRepository.enqueueCoordinateLookups(userId, pendingJobs);

        int coordlessReviewsMatched =
                (int) coordlessReviewOutcomes.stream().filter(MatchOutcome::cidMatched).count();
        int csvCidMatched = (int) csvOutcomes.stream().filter(MatchOutcome::cidMatched).count();
        int csvPlacesCreated = (int) csvOutcomes.stream().filter(MatchOutcome::created).count();
        int csvPlacesMerged = (int) csvOutcomes.stream().filter(MatchOutcome::merged).count();
        List<PlaceId> touchedPlaceIds =
                Stream.concat(coordlessReviewOutcomes.stream(), csvOutcomes.stream())
                        .map(MatchOutcome::placeId)
                        .filter(Objects::nonNull)
                        .toList();
        return new Pass2AccountState(
                state,
                coordlessReviewsMatched,
                csvCidMatched,
                csvPlacesCreated,
                csvPlacesMerged,
                pendingJobs.size(),
                touchedPlaceIds);
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
        String canonicalName = resolveReviewName(review);
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

    private MatchOutcome reconcileCoordlessReview(String accountLabel, ReviewEntry entry) {
        ParsedReview review = entry.review();
        GoogleCid cid = review.googleCid();
        if (cid == null) {
            return new MatchOutcome(
                    new NewResolutionJob(UUID.randomUUID(), entry.recordId()),
                    false,
                    false,
                    false,
                    null);
        }
        String canonicalName = resolveReviewName(review);
        PlaceClassification classification =
                PlaceCategoryClassifier.classify(canonicalName, review.reviewText(), null);
        Optional<PlaceId> matchedId =
                atlasFacade.attachCsvMembershipByCid(
                        new AttachCsvMembershipCommand(
                                cid.unsignedBits(),
                                canonicalName,
                                null,
                                classification.category(),
                                classification.subcategory(),
                                classification.source(),
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
        return matchedId
                .map(placeId -> new MatchOutcome(null, true, false, false, placeId))
                .orElseGet(() ->
                        new MatchOutcome(
                                new NewResolutionJob(UUID.randomUUID(), entry.recordId()),
                                false,
                                false,
                                false,
                                null));
    }

    private MatchOutcome reconcileCsvRow(String accountLabel, CsvRowEntry entry) {
        ParsedSavedCsvRow row = entry.row();
        PlaceClassification classification =
                PlaceCategoryClassifier.classify(row.title(), row.note(), row.tags());
        Long cid = row.googleCid() == null ? null : row.googleCid().unsignedBits();
        Optional<GeoPoint> titleCoords = TitleCoordinateParser.parse(row.title());
        if (cid != null) {
            Optional<PlaceId> matchedId =
                    atlasFacade.attachCsvMembershipByCid(
                            new AttachCsvMembershipCommand(
                                    cid,
                                    row.title(),
                                    titleCoords.orElse(null),
                                    classification.category(),
                                    classification.subcategory(),
                                    classification.source(),
                                    accountLabel,
                                    row.listName(),
                                    row.listKind(),
                                    row.note(),
                                    row.tags(),
                                    row.url(),
                                    null,
                                    null,
                                    null,
                                    null));
            if (matchedId.isPresent()) {
                return new MatchOutcome(null, true, false, false, matchedId.get());
            }
        }
        if (titleCoords.isPresent()) {
            return upsertCsvRowWithCoords(
                    accountLabel, row, classification, cid, titleCoords.get(), CoordSource.TAKEOUT);
        }
        Optional<GeoPoint> s2Coords = GoogleS2CellDecoder.decodeFromUrl(row.url());
        if (s2Coords.isPresent()) {
            return upsertCsvRowWithCoords(
                    accountLabel,
                    row,
                    classification,
                    cid,
                    s2Coords.get(),
                    CoordSource.TAKEOUT_S2_APPROX);
        }
        return new MatchOutcome(
                new NewResolutionJob(UUID.randomUUID(), entry.recordId()),
                false,
                false,
                false,
                null);
    }

    private MatchOutcome upsertCsvRowWithCoords(
            String accountLabel,
            ParsedSavedCsvRow row,
            PlaceClassification classification,
            @Nullable Long cid,
            GeoPoint coordinates,
            CoordSource coordSource) {
        UpsertedPlaceView upserted =
                atlasFacade.upsertImportedPlace(
                        new ImportedPlaceUpsertCommand(
                                row.title(),
                                coordinates,
                                coordSource,
                                null,
                                cid,
                                classification.category(),
                                classification.subcategory(),
                                classification.source(),
                                null,
                                null,
                                accountLabel,
                                row.listName(),
                                row.listKind(),
                                row.note(),
                                row.tags(),
                                row.url(),
                                null,
                                null,
                                null,
                                null));
        return new MatchOutcome(
                null, false, upserted.created(), !upserted.created(), upserted.placeId());
    }

    private static String resolveReviewName(ParsedReview review) {
        String trimmedName = TextNormalizer.trimToNull(review.name());
        return trimmedName != null ? trimmedName : FALLBACK_REVIEW_NAME;
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
                raw.toLowerCase(Locale.ROOT).endsWith(JSON_EXTENSION)
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

    private static int sumInt(IntStream stream) {
        return stream.sum();
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

    private record MatchOutcome(
            @Nullable NewResolutionJob pendingJob,
            boolean cidMatched,
            boolean created,
            boolean merged,
            @Nullable PlaceId placeId) {}
}
