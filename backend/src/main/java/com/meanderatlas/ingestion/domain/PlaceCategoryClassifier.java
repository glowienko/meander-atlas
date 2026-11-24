package com.meanderatlas.ingestion.domain;

import com.meanderatlas.atlas.PlaceCategory;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public final class PlaceCategoryClassifier {

    private static final String WORD_START = "(?:^|[^a-z0-9])(?:";
    private static final String WORD_END = ")(?:$|[^a-z0-9])";
    private static final String RAW_WORD_START = "(?:^|[^\\p{L}0-9])(?:";
    private static final String RAW_WORD_END = ")(?:$|[^\\p{L}0-9])";
    private static final String SUFFIX_START = "(?:^|[a-z0-9\\s-])(?:";
    private static final String ALTERNATION = "|";
    private static final String SPACE = " ";
    private static final String NULL_NAME_MESSAGE = "name must not be null";
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

    public static PlaceClassification classify(String name) {
        return classify(name, null, null);
    }

    public static PlaceClassification classify(
            String name, @Nullable String note, @Nullable String tags) {
        Objects.requireNonNull(name, NULL_NAME_MESSAGE);
        PlaceClassification fromName = classifyText(name);
        if (fromName.category() != PlaceCategory.UNCATEGORIZED) {
            return fromName;
        }
        String secondary = combineSecondary(note, tags);
        if (secondary == null) {
            return UNCATEGORIZED;
        }
        return classifyText(secondary);
    }

    private static PlaceClassification classifyText(String text) {
        String trimmed = TextNormalizer.trimToNull(text);
        if (trimmed == null) {
            return UNCATEGORIZED;
        }
        String loweredRaw = trimmed.toLowerCase(Locale.ROOT);
        String folded = TextNormalizer.normalizeFolded(trimmed);
        for (KeywordRule rule : RULES) {
            String candidate = rule.matchFolded() ? folded : loweredRaw;
            if (rule.pattern().matcher(candidate).find()) {
                return PlaceClassification.heuristic(rule.category(), rule.subcategory());
            }
        }
        return UNCATEGORIZED;
    }

    private static @Nullable String combineSecondary(@Nullable String note, @Nullable String tags) {
        String cleanNote = TextNormalizer.trimToNull(note);
        String cleanTags = TextNormalizer.trimToNull(tags);
        if (cleanNote == null && cleanTags == null) {
            return null;
        }
        if (cleanNote == null) {
            return cleanTags;
        }
        if (cleanTags == null) {
            return cleanNote;
        }
        return cleanNote + SPACE + cleanTags;
    }

    private static KeywordRule words(
            PlaceCategory category, String subcategory, String... keywords) {
        String joined =
                Arrays.stream(keywords)
                        .map(Pattern::quote)
                        .collect(Collectors.joining(ALTERNATION));
        return new KeywordRule(
                category, subcategory, Pattern.compile(WORD_START + joined + WORD_END), true);
    }

    private static KeywordRule rawWords(
            PlaceCategory category, String subcategory, String... keywords) {
        String joined =
                Arrays.stream(keywords)
                        .map(Pattern::quote)
                        .collect(Collectors.joining(ALTERNATION));
        return new KeywordRule(
                category,
                subcategory,
                Pattern.compile(RAW_WORD_START + joined + RAW_WORD_END),
                false);
    }

    private static KeywordRule suffixes(
            PlaceCategory category, String subcategory, String... keywords) {
        String joined =
                Arrays.stream(keywords)
                        .map(Pattern::quote)
                        .collect(Collectors.joining(ALTERNATION));
        return new KeywordRule(
                category, subcategory, Pattern.compile(SUFFIX_START + joined + WORD_END), true);
    }

    private record KeywordRule(
            PlaceCategory category, String subcategory, Pattern pattern, boolean matchFolded) {}
}
