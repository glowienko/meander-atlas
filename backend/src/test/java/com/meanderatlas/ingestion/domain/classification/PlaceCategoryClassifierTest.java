package com.meanderatlas.ingestion.domain.classification;

import static org.assertj.core.api.Assertions.assertThat;

import com.meanderatlas.ingestion.domain.FixtureArchiveSupport;
import com.meanderatlas.ingestion.domain.takeout.ParsedSavedCsvRow;
import com.meanderatlas.ingestion.domain.takeout.TakeoutCsvParser;
import com.meanderatlas.shared.CategorySource;
import com.meanderatlas.shared.PlaceCategory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlaceCategoryClassifierTest {

    @Test
    void classifiesAllElevenCategoriesAcrossPolishAndEnglishNamesWithHeuristicSource() {
        assertClassified("Kościół Mariacki", PlaceCategory.SACRAL, "church");
        assertClassified("Blue Mosque Istanbul", PlaceCategory.SACRAL, "mosque");
        assertClassified("Wawel Cathedral", PlaceCategory.SACRAL, "cathedral");
        assertClassified("Senso-ji Temple", PlaceCategory.SACRAL, "temple");
        assertClassified("Fushimi Inari Taisha", PlaceCategory.SACRAL, "shrine");
        assertClassified("Hallgrimskirkja", PlaceCategory.SACRAL, "church");

        assertClassified("Mount Fuji Fifth Station", PlaceCategory.MOUNTAIN, "peak");
        assertClassified("Szczyt Śnieżka", PlaceCategory.MOUNTAIN, "peak");
        assertClassified("Lodowiec Jostedalsbreen", PlaceCategory.MOUNTAIN, "glacier");

        assertClassified("Wodospad Kamieńczyka", PlaceCategory.WATER, "waterfall");
        assertClassified("Skogafoss Waterfall", PlaceCategory.WATER, "waterfall");
        assertClassified("Rzeka Wisła", PlaceCategory.WATER, "river");
        assertClassified("Jezioro Morskie Oko", PlaceCategory.WATER, "lake");
        assertClassified("Plaża w Sopocie", PlaceCategory.WATER, "beach");

        assertClassified("Tatrzański Park Narodowy", PlaceCategory.NATURE_PARK, "national_park");
        assertClassified("Ogród Botaniczny UJ", PlaceCategory.NATURE_PARK, "botanical_garden");
        assertClassified("Park Guell", PlaceCategory.NATURE_PARK, "park");

        assertClassified("Punkt widokowy Gubałówka", PlaceCategory.VIEWPOINT, "viewpoint");
        assertClassified("Mirador San Nicolas", PlaceCategory.VIEWPOINT, "viewpoint");
        assertClassified("Wieża widokowa na Śnieżniku", PlaceCategory.VIEWPOINT, "viewpoint");

        assertClassified("Wawel Royal Castle", PlaceCategory.LANDMARK, "castle");
        assertClassified("Schonbrunn Palace", PlaceCategory.LANDMARK, "palace");
        assertClassified("Golden Gate Bridge", PlaceCategory.LANDMARK, "bridge");
        assertClassified("Eiffel Tower", PlaceCategory.LANDMARK, "tower");
        assertClassified("Rynek Glowny Krakow", PlaceCategory.LANDMARK, "square");
        assertClassified("Plaza Mayor Madrid", PlaceCategory.LANDMARK, "square");

        assertClassified("Rijksmuseum", PlaceCategory.MUSEUM, "museum");
        assertClassified("Muzeum Narodowe w Krakowie", PlaceCategory.MUSEUM, "museum");

        assertClassified("Restauracja Pod Aniołami", PlaceCategory.RESTAURANT, "restaurant");
        assertClassified("Bar Mleczny Pod Temidą", PlaceCategory.RESTAURANT, "milk_bar");
        assertClassified("Nolio Pizzeria", PlaceCategory.RESTAURANT, "pizzeria");

        assertClassified("Kawiarnia Camelot", PlaceCategory.CAFE_BAR, "cafe");
        assertClassified("Browar Lubicz", PlaceCategory.CAFE_BAR, "brewery");
        assertClassified("Irish Pub Krakow", PlaceCategory.CAFE_BAR, "pub");

        assertClassified("Palace Hotel Tokyo", PlaceCategory.HOTEL, "hotel");
        assertClassified("Hotel Plaża Sopot", PlaceCategory.HOTEL, "hotel");
        assertClassified(
                "Schronisko PTTK w Dolinie Pięciu Stawów", PlaceCategory.HOTEL, "mountain_hut");

        PlaceClassification unknown = PlaceCategoryClassifier.classify("Nyhavn", null, null);
        assertThat(unknown.category()).isEqualTo(PlaceCategory.UNCATEGORIZED);
        assertThat(unknown.subcategory()).isNull();
        assertThat(unknown.source()).isEqualTo(CategorySource.HEURISTIC);
    }

    @Test
    void avoidsSubstringFalsePositivesAndPrefersNameOverNoteAndTags() {
        PlaceClassification museumWithLandmarkTag =
                PlaceCategoryClassifier.classify(
                        "Rijksmuseum", "Synthetic landmark note", "landmark");
        assertThat(museumWithLandmarkTag.category()).isEqualTo(PlaceCategory.MUSEUM);
        assertThat(museumWithLandmarkTag.subcategory()).isEqualTo("museum");

        PlaceClassification mountWithPublicNote =
                PlaceCategoryClassifier.classify(
                        "Mount Fuji Fifth Station", "Public landmark bucket list", "bucket-list");
        assertThat(mountWithPublicNote.category()).isEqualTo(PlaceCategory.MOUNTAIN);

        PlaceClassification barcelonaUnknown =
                PlaceCategoryClassifier.classify("Barcelona", "Public trip note", "vienna");
        assertThat(barcelonaUnknown.category()).isEqualTo(PlaceCategory.UNCATEGORIZED);

        PlaceClassification noteFallback =
                PlaceCategoryClassifier.classify("Alhambra", "Must-see castle ruin", null);
        assertThat(noteFallback.category()).isEqualTo(PlaceCategory.LANDMARK);
        assertThat(noteFallback.subcategory()).isEqualTo("castle");
        assertThat(noteFallback.source()).isEqualTo(CategorySource.HEURISTIC);
    }

    @Test
    void classifiesSyntheticTakeoutRowsDeterministicallyWithHeuristicSource(@TempDir Path tempDir)
            throws IOException {
        Path root = FixtureArchiveSupport.extractSyntheticTakeout(tempDir);
        List<ParsedSavedCsvRow> rows =
                TakeoutCsvParser.parse(root.resolve("Takeout/Zapisano/Chcę odwiedzić.csv"));

        assertThat(rows)
                .hasSize(15)
                .allSatisfy(row -> {
                    PlaceClassification result =
                            PlaceCategoryClassifier.classify(row.title(), row.note(), row.tags());
                    assertThat(result.source()).isEqualTo(CategorySource.HEURISTIC);
                    assertThat(result.category()).isNotEqualTo(PlaceCategory.UNCATEGORIZED);
                });
    }

    private static void assertClassified(
            String name, PlaceCategory expectedCategory, String expectedSubcategory) {
        PlaceClassification classification = PlaceCategoryClassifier.classify(name, null, null);
        assertThat(classification.category()).as(name).isEqualTo(expectedCategory);
        assertThat(classification.subcategory()).as(name).isEqualTo(expectedSubcategory);
        assertThat(classification.source()).as(name).isEqualTo(CategorySource.HEURISTIC);
    }
}
