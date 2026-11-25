package com.meanderatlas.ingestion.domain.classification;

import com.meanderatlas.ingestion.domain.TextNormalizer;
import com.meanderatlas.shared.PlaceCategory;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public final class PlaceCategoryClassifier {

    private static final String WORD_END = ")(?:$|[^a-z0-9])";
    private static final PlaceClassification UNCATEGORIZED =
            PlaceClassification.heuristic(PlaceCategory.UNCATEGORIZED, null);

    private static final List<KeywordRule> RULES =
            List.of(
                    words(PlaceCategory.HOTEL, "mountain_hut", "schronisko", "mountain hut"),
                    words(PlaceCategory.HOTEL, "hostel", "hostel"),
                    words(PlaceCategory.HOTEL, "resort", "resort"),
                    words(PlaceCategory.HOTEL, "campground", "kemping", "camping", "campground"),
                    words(
                            PlaceCategory.HOTEL,
                            "hotel",
                            "hotel",
                            "motel",
                            "pensjonat",
                            "guesthouse",
                            "guest house",
                            "apartament",
                            "apartments",
                            "noclegi",
                            "agroturystyka",
                            "ryokan",
                            "riad",
                            "lodge",
                            "inn",
                            "bed and breakfast"),
                    suffixes(PlaceCategory.MUSEUM, "museum", "museum", "muzeum"),
                    words(
                            PlaceCategory.MUSEUM,
                            "gallery",
                            "galeria sztuki",
                            "art gallery",
                            "gallery",
                            "skansen",
                            "planetarium",
                            "exhibition",
                            "wystawa"),
                    words(PlaceCategory.SACRAL, "cathedral", "katedra", "cathedral", "duomo"),
                    words(PlaceCategory.SACRAL, "basilica", "bazylika", "basilica"),
                    words(PlaceCategory.SACRAL, "chapel", "kaplica", "chapel"),
                    words(PlaceCategory.SACRAL, "mosque", "meczet", "mosque", "masjid"),
                    words(PlaceCategory.SACRAL, "synagogue", "synagoga", "synagogue"),
                    words(
                            PlaceCategory.SACRAL,
                            "monastery",
                            "klasztor",
                            "monastery",
                            "opactwo",
                            "abbey"),
                    words(PlaceCategory.SACRAL, "shrine", "shrine", "taisha", "jinja"),
                    words(PlaceCategory.SACRAL, "temple", "swiatynia", "temple", "wat", "pagoda"),
                    words(PlaceCategory.SACRAL, "sanctuary", "sanktuarium", "sanctuary"),
                    suffixes(PlaceCategory.SACRAL, "church", "kirche", "kirkja"),
                    words(
                            PlaceCategory.SACRAL,
                            "church",
                            "kosciol",
                            "church",
                            "cerkiew",
                            "parafia"),
                    words(
                            PlaceCategory.VIEWPOINT,
                            "viewpoint",
                            "punkt widokowy",
                            "taras widokowy",
                            "wieza widokowa",
                            "viewpoint",
                            "view point",
                            "lookout",
                            "overlook",
                            "scenic overlook",
                            "belvedere",
                            "mirador",
                            "panorama",
                            "observation deck",
                            "skydeck",
                            "vista point"),
                    words(PlaceCategory.MOUNTAIN, "glacier", "lodowiec", "glacier"),
                    words(PlaceCategory.MOUNTAIN, "volcano", "wulkan", "volcano"),
                    words(PlaceCategory.MOUNTAIN, "pass", "przelecz", "pass"),
                    words(PlaceCategory.MOUNTAIN, "canyon", "kanion", "canyon", "wawoz", "gorge"),
                    words(PlaceCategory.MOUNTAIN, "cave", "jaskinia", "grota", "cave"),
                    words(PlaceCategory.MOUNTAIN, "valley", "dolina", "valley"),
                    words(PlaceCategory.MOUNTAIN, "cliff", "klif", "cliff"),
                    words(
                            PlaceCategory.MOUNTAIN,
                            "peak",
                            "szczyt",
                            "peak",
                            "mount",
                            "mt",
                            "turnia"),
                    words(
                            PlaceCategory.MOUNTAIN,
                            "mountain",
                            "gora",
                            "gory",
                            "mountain",
                            "polonina",
                            "ridge",
                            "grzbiet"),
                    suffixes(PlaceCategory.WATER, "waterfall", "foss"),
                    words(PlaceCategory.WATER, "waterfall", "wodospad", "waterfall", "falls"),
                    words(PlaceCategory.WATER, "river", "rzeka", "river"),
                    words(
                            PlaceCategory.WATER,
                            "lake",
                            "jezioro",
                            "lake",
                            "staw",
                            "lagoon",
                            "laguna"),
                    rawWords(PlaceCategory.WATER, "beach", "pla\u017Ca"),
                    words(PlaceCategory.WATER, "beach", "beach"),
                    words(PlaceCategory.WATER, "fjord", "fiord", "fjord"),
                    words(
                            PlaceCategory.WATER,
                            "hot_spring",
                            "zrodla termalne",
                            "termy",
                            "hot spring",
                            "hot springs",
                            "geyser",
                            "gejzer"),
                    words(PlaceCategory.WATER, "bay", "zatoka", "bay", "cove"),
                    words(PlaceCategory.WATER, "island", "wyspa", "island"),
                    words(PlaceCategory.WATER, "canal", "kanal", "canal"),
                    words(
                            PlaceCategory.NATURE_PARK,
                            "national_park",
                            "park narodowy",
                            "national park"),
                    words(
                            PlaceCategory.NATURE_PARK,
                            "botanical_garden",
                            "ogrod botaniczny",
                            "botanical garden",
                            "arboretum"),
                    words(
                            PlaceCategory.NATURE_PARK,
                            "nature_reserve",
                            "rezerwat",
                            "nature reserve",
                            "park krajobrazowy",
                            "state park",
                            "regional park"),
                    words(PlaceCategory.NATURE_PARK, "forest", "las", "puszcza", "forest", "woods"),
                    words(PlaceCategory.NATURE_PARK, "park", "park", "ogrod", "garden"),
                    words(PlaceCategory.RESTAURANT, "milk_bar", "bar mleczny"),
                    words(PlaceCategory.RESTAURANT, "pizzeria", "pizzeria"),
                    words(PlaceCategory.RESTAURANT, "bistro", "bistro"),
                    words(PlaceCategory.RESTAURANT, "ramen", "ramen"),
                    words(PlaceCategory.RESTAURANT, "sushi", "sushi", "izakaya"),
                    words(PlaceCategory.RESTAURANT, "steakhouse", "steakhouse"),
                    words(PlaceCategory.RESTAURANT, "burger", "burger", "burgerownia"),
                    words(
                            PlaceCategory.RESTAURANT,
                            "tavern",
                            "karczma",
                            "gospoda",
                            "tawerna",
                            "taverna"),
                    words(
                            PlaceCategory.RESTAURANT,
                            "restaurant",
                            "restauracja",
                            "restaurant",
                            "trattoria",
                            "osteria",
                            "brasserie",
                            "pierogarnia",
                            "jadlodajnia",
                            "diner",
                            "eatery",
                            "tapas",
                            "smazalnia",
                            "seafood"),
                    words(
                            PlaceCategory.CAFE_BAR,
                            "cafe",
                            "kawiarnia",
                            "cafe",
                            "coffee",
                            "espresso",
                            "roastery",
                            "palarnia kawy"),
                    words(
                            PlaceCategory.CAFE_BAR,
                            "bakery",
                            "cukiernia",
                            "piekarnia",
                            "bakery",
                            "patisserie",
                            "lodziarnia",
                            "gelateria"),
                    words(
                            PlaceCategory.CAFE_BAR,
                            "brewery",
                            "browar",
                            "brewery",
                            "taproom",
                            "piwiarnia"),
                    words(PlaceCategory.CAFE_BAR, "winery", "winiarnia", "winery", "wine bar"),
                    words(
                            PlaceCategory.CAFE_BAR,
                            "tea_house",
                            "herbaciarnia",
                            "tea house",
                            "teahouse"),
                    words(PlaceCategory.CAFE_BAR, "pub", "pub"),
                    words(PlaceCategory.CAFE_BAR, "bar", "bar", "cocktail", "koktajl"),
                    words(
                            PlaceCategory.LANDMARK,
                            "castle",
                            "zamek",
                            "castle",
                            "chateau",
                            "schloss",
                            "twierdza",
                            "fortress",
                            "fort",
                            "cytadela",
                            "citadel"),
                    words(PlaceCategory.LANDMARK, "palace", "palac", "palace"),
                    words(PlaceCategory.LANDMARK, "bridge", "most", "bridge", "wiadukt", "viaduct"),
                    words(PlaceCategory.LANDMARK, "tower", "wieza", "tower"),
                    words(PlaceCategory.LANDMARK, "gate", "brama", "gate", "arch"),
                    words(
                            PlaceCategory.LANDMARK,
                            "square",
                            "rynek",
                            "plac",
                            "square",
                            "piazza",
                            "plaza"),
                    words(
                            PlaceCategory.LANDMARK,
                            "monument",
                            "pomnik",
                            "monument",
                            "statue",
                            "memorial"),
                    words(
                            PlaceCategory.LANDMARK,
                            "ruins",
                            "ruiny",
                            "ruins",
                            "acropolis",
                            "akropol",
                            "amphitheatre",
                            "amfiteatr"),
                    words(
                            PlaceCategory.LANDMARK,
                            "opera_house",
                            "opera",
                            "filharmonia",
                            "teatr",
                            "theatre",
                            "theater"),
                    words(PlaceCategory.LANDMARK, "lighthouse", "latarnia", "lighthouse"),
                    words(PlaceCategory.LANDMARK, "town_hall", "ratusz", "town hall"),
                    words(PlaceCategory.LANDMARK, "old_town", "stare miasto", "old town"),
                    words(PlaceCategory.LANDMARK, "landmark", "landmark", "zabytek"));

    private PlaceCategoryClassifier() {}

    public static PlaceClassification classify(
            String name, @Nullable String note, @Nullable String tags) {
        Objects.requireNonNull(name, "name must not be null");
        PlaceClassification fromName = classifyText(name);
        if (fromName.category() != PlaceCategory.UNCATEGORIZED) {
            return fromName;
        }
        String secondary =
                Stream.of(note, tags)
                        .map(TextNormalizer::trimToNull)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining(" "));
        return secondary.isEmpty() ? UNCATEGORIZED : classifyText(secondary);
    }

    private static PlaceClassification classifyText(String text) {
        String trimmed = TextNormalizer.trimToNull(text);
        if (trimmed == null) {
            return UNCATEGORIZED;
        }
        String loweredRaw = trimmed.toLowerCase(Locale.ROOT);
        String folded = TextNormalizer.normalizeFolded(trimmed);
        return RULES.stream()
                .filter(rule -> rule.matches(folded, loweredRaw))
                .findFirst()
                .map(KeywordRule::toClassification)
                .orElse(UNCATEGORIZED);
    }

    private static KeywordRule words(
            PlaceCategory category, String subcategory, String... keywords) {
        return compileRule(category, subcategory, "(?:^|[^a-z0-9])(?:", WORD_END, true, keywords);
    }

    private static KeywordRule rawWords(
            PlaceCategory category, String subcategory, String... keywords) {
        return compileRule(
                category,
                subcategory,
                "(?:^|[^\\p{L}0-9])(?:",
                ")(?:$|[^\\p{L}0-9])",
                false,
                keywords);
    }

    private static KeywordRule suffixes(
            PlaceCategory category, String subcategory, String... keywords) {
        return compileRule(
                category, subcategory, "(?:^|[a-z0-9\\s-])(?:", WORD_END, true, keywords);
    }

    private static KeywordRule compileRule(
            PlaceCategory category,
            String subcategory,
            String prefix,
            String suffix,
            boolean matchFolded,
            String... keywords) {
        String joined =
                Arrays.stream(keywords).map(Pattern::quote).collect(Collectors.joining("|"));
        return new KeywordRule(
                category, subcategory, Pattern.compile(prefix + joined + suffix), matchFolded);
    }

    private record KeywordRule(
            PlaceCategory category, String subcategory, Pattern pattern, boolean matchFolded) {

        boolean matches(String folded, String loweredRaw) {
            return pattern.matcher(matchFolded ? folded : loweredRaw).find();
        }

        PlaceClassification toClassification() {
            return PlaceClassification.heuristic(category, subcategory);
        }
    }
}
