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
        assertClassified("Refugio Frey Bariloche", PlaceCategory.HOTEL, "mountain_hut");
        assertClassified("Hostal Sol Madrid", PlaceCategory.HOTEL, "hostel");

        assertClassified("Miradouro da Senhora do Monte", PlaceCategory.VIEWPOINT, "viewpoint");
        assertClassified("Burg Eltz", PlaceCategory.LANDMARK, "castle");
        assertClassified("Palazzo Pitti", PlaceCategory.LANDMARK, "palace");
        assertClassified("Mercado de San Miguel", PlaceCategory.LANDMARK, "market");
        assertClassified("Iglesia de San Francisco", PlaceCategory.SACRAL, "church");
        assertClassified("Cerro Fitz Roy", PlaceCategory.MOUNTAIN, "peak");
        assertClassified("Playa de las Catedrales", PlaceCategory.WATER, "beach");
        assertClassified("Lago di Como", PlaceCategory.WATER, "lake");
        assertClassified("Hakone Onsen", PlaceCategory.WATER, "hot_spring");
        assertClassified("Museo del Prado", PlaceCategory.MUSEUM, "museum");
        assertClassified("Parco Sempione", PlaceCategory.NATURE_PARK, "park");
        assertClassified("Trattoria da Enzo", PlaceCategory.RESTAURANT, "restaurant");
        assertClassified("Boulangerie Poilane", PlaceCategory.CAFE_BAR, "bakery");

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

        assertClassified("See You Cafe", PlaceCategory.CAFE_BAR, "cafe");
        assertClassified("Berghütte Alpenblick", PlaceCategory.HOTEL, "mountain_hut");
        assertClassified("Restaurante Las Brasas", PlaceCategory.RESTAURANT, "restaurant");
        assertClassified("Cafe Las Flores", PlaceCategory.CAFE_BAR, "cafe");
        assertClassified("Plaza de las Flores", PlaceCategory.LANDMARK, "square");
        assertClassified("Castillo de las Coloradas", PlaceCategory.LANDMARK, "castle");
        assertClassified("Palacio de las Dueñas", PlaceCategory.LANDMARK, "palace");
        assertClassified("Praça do Comércio", PlaceCategory.LANDMARK, "square");
        assertClassified("Inn River", PlaceCategory.WATER, "river");
        assertClassified("Coney Island Burger", PlaceCategory.RESTAURANT, "burger");
        assertClassified("Holiday Inn", PlaceCategory.HOTEL, "hotel");
        assertThat(PlaceCategoryClassifier.classify("Las Vegas", null, null).category())
                .isEqualTo(PlaceCategory.UNCATEGORIZED);
        assertThat(PlaceCategoryClassifier.classify("Las Ramblas", null, null).category())
                .isEqualTo(PlaceCategory.UNCATEGORIZED);
        assertThat(PlaceCategoryClassifier.classify("Las Palmas", null, null).category())
                .isEqualTo(PlaceCategory.UNCATEGORIZED);
        assertThat(PlaceCategoryClassifier.classify("JR Pass Exchange", null, null).category())
                .isEqualTo(PlaceCategory.UNCATEGORIZED);
        assertThat(
                        PlaceCategoryClassifier.classify("Random Spot", "praca w centrum", null)
                                .category())
                .isEqualTo(PlaceCategory.UNCATEGORIZED);
        assertThat(PlaceCategoryClassifier.classify("Salzburg", null, null).category())
                .isEqualTo(PlaceCategory.UNCATEGORIZED);
        assertThat(PlaceCategoryClassifier.classify("Walking Tour", null, null).category())
                .isEqualTo(PlaceCategory.UNCATEGORIZED);
        assertThat(PlaceCategoryClassifier.classify("Galeria Krakowska", null, null).category())
                .isEqualTo(PlaceCategory.UNCATEGORIZED);
        assertThat(
                        PlaceCategoryClassifier.classify("Q-Park Markt Hilversum", null, null)
                                .category())
                .isEqualTo(PlaceCategory.UNCATEGORIZED);
        assertThat(PlaceCategoryClassifier.classify("Central Car Park", null, null).category())
                .isEqualTo(PlaceCategory.UNCATEGORIZED);
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
