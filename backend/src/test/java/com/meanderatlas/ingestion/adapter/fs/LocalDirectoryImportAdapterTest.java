package com.meanderatlas.ingestion.adapter.fs;

import static org.assertj.core.api.Assertions.assertThat;

import com.meanderatlas.ingestion.domain.FixtureArchiveSupport;
import com.meanderatlas.ingestion.domain.source.AccountImportSource;
import com.meanderatlas.ingestion.domain.source.DiscoveredImportFile;
import com.meanderatlas.ingestion.domain.source.ImportFileKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDirectoryImportAdapterTest {

    @TempDir Path importsDir;

    @Test
    void discoversExtractedTakeoutAndTimelineWhileIgnoringArchivesAndHiddenFiles()
            throws IOException {
        Path personalDir = importsDir.resolve("personal");
        FixtureArchiveSupport.extractSyntheticTakeout(personalDir);
        Files.copy(
                FixtureArchiveSupport.syntheticTimelinePath(),
                personalDir.resolve("Timeline.json"));
        Files.writeString(personalDir.resolve("takeout.zip"), "zip-bytes", StandardCharsets.UTF_8);
        Files.writeString(personalDir.resolve(".DS_Store"), "hidden", StandardCharsets.UTF_8);
        Files.writeString(
                personalDir.resolve("Oznaczone miejsca.json"),
                "{\"features\":[]}",
                StandardCharsets.UTF_8);

        Path workDir = importsDir.resolve("work");
        Files.createDirectories(workDir);
        Files.copy(FixtureArchiveSupport.syntheticTimelinePath(), workDir.resolve("Timeline.json"));

        LocalDirectoryImportAdapter adapter =
                new LocalDirectoryImportAdapter(importsDir.toString());

        List<AccountImportSource> accounts = adapter.discoverAccounts(null);
        assertThat(accounts)
                .extracting(AccountImportSource::accountLabel)
                .containsExactly("personal", "work");

        AccountImportSource personal = accounts.getFirst();
        assertThat(personal.contentSha256()).hasSize(64);
        assertThat(personal.files()).hasSize(6);
        assertThat(personal.files())
                .extracting(DiscoveredImportFile::kind)
                .containsExactlyInAnyOrder(
                        ImportFileKind.TIMELINE_JSON,
                        ImportFileKind.REVIEW_GEOJSON,
                        ImportFileKind.STARRED_GEOJSON,
                        ImportFileKind.SAVED_CSV,
                        ImportFileKind.SAVED_CSV,
                        ImportFileKind.SAVED_CSV);

        List<AccountImportSource> filtered = adapter.discoverAccounts("work");
        assertThat(filtered).hasSize(1);
        assertThat(filtered.getFirst().accountLabel()).isEqualTo("work");
        assertThat(filtered.getFirst().files())
                .extracting(DiscoveredImportFile::kind)
                .containsExactly(ImportFileKind.TIMELINE_JSON);
    }

    @Test
    void computesSameSha256AcrossRepeatedScansAndChangesWhenContentChanges() throws IOException {
        Path accountDir = importsDir.resolve("personal");
        Files.createDirectories(accountDir);
        Path timeline = accountDir.resolve("Timeline.json");
        Files.copy(FixtureArchiveSupport.syntheticTimelinePath(), timeline);

        LocalDirectoryImportAdapter adapter =
                new LocalDirectoryImportAdapter(importsDir.toString());
        String initialHash = adapter.discoverAccounts(null).getFirst().contentSha256();

        Files.writeString(accountDir.resolve("ignored.zip"), "zip-noise", StandardCharsets.UTF_8);
        assertThat(adapter.discoverAccounts(null).getFirst().contentSha256())
                .isEqualTo(initialHash);

        Files.writeString(timeline, "{\"semanticSegments\":[]}", StandardCharsets.UTF_8);
        assertThat(adapter.discoverAccounts(null).getFirst().contentSha256())
                .isNotEqualTo(initialHash);
    }
}
