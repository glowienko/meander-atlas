package com.meanderatlas.atlas.application;

import com.meanderatlas.atlas.BoundarySeedResultView;
import com.meanderatlas.atlas.domain.RegionLevel;
import com.meanderatlas.atlas.domain.boundaries.BoundaryFeature;
import com.meanderatlas.atlas.domain.boundaries.NaturalEarthGeoJsonParser;
import com.meanderatlas.atlas.domain.boundaries.RegionBoundaryRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class SeedRegionBoundaries {

    private static final String COUNTRIES_FILE = "ne_10m_admin_0_countries.geojson";
    private static final String ADMIN1_FILE = "ne_10m_admin_1_states_provinces.geojson";
    private static final double COUNTRY_SIMPLIFY_TOLERANCE = 0.02;
    private static final double ADMIN1_SIMPLIFY_TOLERANCE = 0.005;

    private final RegionBoundaryRepository regionBoundaryRepository;
    private final Path boundariesDir;
    private final TransactionOperations transactionOperations;

    public SeedRegionBoundaries(
            RegionBoundaryRepository regionBoundaryRepository,
            @Value("${app.boundaries-dir:${user.home}/meander-atlas-data/boundaries}")
                    String boundariesDir,
            TransactionOperations transactionOperations) {
        this.regionBoundaryRepository = regionBoundaryRepository;
        this.boundariesDir = Path.of(boundariesDir).toAbsolutePath().normalize();
        this.transactionOperations = transactionOperations;
    }

    public BoundarySeedResultView execute(boolean force) {
        long existingCountries = regionBoundaryRepository.countByLevel(RegionLevel.COUNTRY);
        long existingAdmin1 = regionBoundaryRepository.countByLevel(RegionLevel.ADMIN1);
        if (!force && existingCountries > 0 && existingAdmin1 > 0) {
            int tagged = assignRegionsInTransaction();
            return new BoundarySeedResultView(0, 0, tagged, true);
        }

        Path countriesPath = boundariesDir.resolve(COUNTRIES_FILE);
        Path admin1Path = boundariesDir.resolve(ADMIN1_FILE);
        boolean hasCountriesFile = Files.isRegularFile(countriesPath);
        boolean hasAdmin1File = Files.isRegularFile(admin1Path);
        if (!hasCountriesFile && !hasAdmin1File) {
            return new BoundarySeedResultView(0, 0, 0, true);
        }

        int countriesUpserted =
                (force || existingCountries == 0) && hasCountriesFile
                        ? parseAndUpsertLevel(
                                countriesPath,
                                COUNTRY_SIMPLIFY_TOLERANCE,
                                NaturalEarthGeoJsonParser::parseCountries)
                        : 0;
        int admin1Upserted =
                (force || existingAdmin1 == 0) && hasAdmin1File
                        ? parseAndUpsertLevel(
                                admin1Path,
                                ADMIN1_SIMPLIFY_TOLERANCE,
                                NaturalEarthGeoJsonParser::parseAdmin1)
                        : 0;
        int placesTagged = assignRegionsInTransaction();
        return new BoundarySeedResultView(
                countriesUpserted,
                admin1Upserted,
                placesTagged,
                countriesUpserted == 0 && admin1Upserted == 0);
    }

    private int parseAndUpsertLevel(Path file, double tolerance, BoundaryParser parser) {
        List<BoundaryFeature> features;
        try {
            features = parser.parse(file);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read boundary GeoJSON: " + file, ex);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid boundary GeoJSON: " + file, ex);
        }
        return Objects.requireNonNullElse(
                transactionOperations.execute(
                        _ -> regionBoundaryRepository.upsertBoundaries(features, tolerance)),
                0);
    }

    private int assignRegionsInTransaction() {
        return Objects.requireNonNullElse(
                transactionOperations.execute(
                        _ -> regionBoundaryRepository.assignRegionsToUntaggedPlaces()),
                0);
    }

    @FunctionalInterface
    private interface BoundaryParser {
        List<BoundaryFeature> parse(Path file) throws IOException;
    }
}
