package com.meanderatlas.ingestion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.meanderatlas.atlas.ListKind;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TakeoutCsvParserTest {

    @Test
    void parsesAllCsvFilesFromSyntheticTakeoutArchiveAndSkipsNonMapsCsv(@TempDir Path tempDir)
            throws IOException {
        Path root = FixtureArchiveSupport.extractSyntheticTakeout(tempDir);

        List<ParsedSavedCsvRow> plWantToGo =
                TakeoutCsvParser.parse(root.resolve("Takeout/Zapisano/Chcę odwiedzić.csv"));
        List<ParsedSavedCsvRow> plFavorites =
                TakeoutCsvParser.parse(root.resolve("Takeout/Zapisano/Ulubione miejsca.csv"));
        List<ParsedSavedCsvRow> enWantToGo =
                TakeoutCsvParser.parse(root.resolve("Takeout/Saved/Want to go.csv"));
        List<ParsedSavedCsvRow> images =
                TakeoutCsvParser.parse(root.resolve("Takeout/Zapisano/Obrazy.csv"));

        assertThat(plWantToGo).hasSize(15);
        ParsedSavedCsvRow wawel = plWantToGo.getFirst();
        assertThat(wawel.listName()).isEqualTo("Chcę odwiedzić");
        assertThat(wawel.listKind()).isEqualTo(ListKind.WANT_TO_GO);
        assertThat(wawel.title()).isEqualTo("Wawel Royal Castle");
        assertThat(wawel.googleCid()).isEqualTo(GoogleCid.fromUnsignedDecimal("536870913"));
        assertThat(wawel.note()).isEqualTo("Synthetic landmark note");
        assertThat(wawel.tags()).isEqualTo("landmark");
        assertThat(wawel.comment()).isNull();

        assertThat(plFavorites).hasSize(5);
        ParsedSavedCsvRow schonbrunn = plFavorites.getFirst();
        assertThat(schonbrunn.listName()).isEqualTo("Ulubione miejsca");
        assertThat(schonbrunn.listKind()).isEqualTo(ListKind.FAVORITE);
        assertThat(schonbrunn.title()).isEqualTo("Schonbrunn Palace");
        assertThat(schonbrunn.googleCid()).isEqualTo(GoogleCid.fromUnsignedDecimal("536870928"));
        assertThat(schonbrunn.note()).isEqualTo("Public architectural highlight");
        assertThat(schonbrunn.tags()).isEqualTo("favorite");

        assertThat(enWantToGo).hasSize(5);
        ParsedSavedCsvRow hallgrimskirkja = enWantToGo.getFirst();
        assertThat(hallgrimskirkja.listName()).isEqualTo("Want to go");
        assertThat(hallgrimskirkja.listKind()).isEqualTo(ListKind.WANT_TO_GO);
        assertThat(hallgrimskirkja.title()).isEqualTo("Hallgrimskirkja");
        assertThat(hallgrimskirkja.googleCid())
                .isEqualTo(GoogleCid.fromUnsignedDecimal("536870933"));

        assertThat(images).isEmpty();
    }

    @Test
    void normalizesNbspBomPreambleAndQuotedMultilineFields() {
        String content =
                "\uFEFFCustom list preamble line 1\n"
                        + "Second description line, with comma\n"
                        + "\n"
                        + "Tytuł\u00A0,Notatka\u00A0,URL\u00A0,Tagi,Skomentuj\n"
                        + "\"Cafe \"\"Roma\"\", Old Town\",\"Line 1\nLine 2\","
                        + "https://www.google.com/maps/place/Cafe+Roma/data=!4m2!3m1!1s0x1:0xf123456789abcdef,"
                        + "coffee,\"Best espresso, ever\"\n"
                        + "Non-Maps Bookmark,ignore,https://example.com/article,web,\n"
                        + "Spoofed Query,ignore,https://example.com/?ref=google.com/maps,web,\n"
                        + "Regional Maps,,https://www.google.pl/maps/place/Test/data=!4m2!3m1!1s0x1:0x20000001,,\n";

        List<ParsedSavedCsvRow> rows =
                TakeoutCsvParser.parse("Chcę\u00A0odwiedzić(1).csv", content);

        assertThat(rows).hasSize(2);
        ParsedSavedCsvRow row = rows.getFirst();
        assertThat(row.listName()).isEqualTo("Chcę odwiedzić(1)");
        assertThat(row.listKind()).isEqualTo(ListKind.WANT_TO_GO);
        assertThat(row.title()).isEqualTo("Cafe \"Roma\", Old Town");
        assertThat(row.note()).isEqualTo("Line 1\nLine 2");
        assertThat(row.googleCid())
                .isEqualTo(GoogleCid.fromUnsignedDecimal("17375808098319191535"));
        assertThat(row.tags()).isEqualTo("coffee");
        assertThat(row.comment()).isEqualTo("Best espresso, ever");
        assertThat(rows.get(1).title()).isEqualTo("Regional Maps");
        assertThat(rows.get(1).googleCid()).isEqualTo(GoogleCid.fromUnsignedDecimal("536870913"));
    }

    @Test
    void mapsPlAndEnListFilenamesToExpectedListKinds() {
        assertThat(TakeoutCsvParser.classifyListKind("Want to go.csv"))
                .isEqualTo(ListKind.WANT_TO_GO);
        assertThat(TakeoutCsvParser.classifyListKind("Chcę odwiedzić.csv"))
                .isEqualTo(ListKind.WANT_TO_GO);
        assertThat(TakeoutCsvParser.classifyListKind("Chcę odwiedzić(2).csv"))
                .isEqualTo(ListKind.WANT_TO_GO);
        assertThat(TakeoutCsvParser.classifyListKind("Ulubione miejsca.csv"))
                .isEqualTo(ListKind.FAVORITE);
        assertThat(TakeoutCsvParser.classifyListKind("Favorite places.csv"))
                .isEqualTo(ListKind.FAVORITE);
        assertThat(TakeoutCsvParser.classifyListKind("Favourite places.csv"))
                .isEqualTo(ListKind.FAVORITE);
        assertThat(TakeoutCsvParser.classifyListKind("Starred places.csv"))
                .isEqualTo(ListKind.STARRED);
        assertThat(TakeoutCsvParser.classifyListKind("Miejsca oznaczone gwiazdką.csv"))
                .isEqualTo(ListKind.STARRED);
        assertThat(TakeoutCsvParser.classifyListKind("Domyślna lista(1).csv"))
                .isEqualTo(ListKind.CUSTOM);
        assertThat(TakeoutCsvParser.classifyListKind("Krakow Food.csv")).isEqualTo(ListKind.CUSTOM);
        assertThat(TakeoutCsvParser.isNonMapsCsv("Obrazy.csv")).isTrue();
        assertThat(TakeoutCsvParser.isNonMapsCsv("Images.csv")).isTrue();
        assertThat(TakeoutCsvParser.isNonMapsCsv("Want to go.csv")).isFalse();
    }
}
