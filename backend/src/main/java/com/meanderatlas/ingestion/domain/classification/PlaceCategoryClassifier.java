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
                    suffixes(PlaceCategory.HOTEL, "mountain_hut", "hutte"),
                    words(
                            PlaceCategory.HOTEL,
                            "mountain_hut",
                            "schronisko",
                            "mountain hut",
                            "refugio",
                            "rifugio",
                            "chalet"),
                    words(PlaceCategory.HOTEL, "hostel", "hostel", "hostal", "albergue", "auberge"),
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
                            "bed and breakfast",
                            "posada",
                            "pousada",
                            "pensione"),
                    suffixes(PlaceCategory.MUSEUM, "museum", "museum", "muzeum"),
                    words(PlaceCategory.MUSEUM, "museum", "museo", "musee", "museu"),
                    words(
                            PlaceCategory.MUSEUM,
                            "gallery",
                            "galeria sztuki",
                            "art gallery",
                            "gallery",
                            "galerie",
                            "galleria",
                            "skansen",
                            "planetarium",
                            "exhibition",
                            "wystawa"),
                    words(
                            PlaceCategory.SACRAL,
                            "cathedral",
                            "katedra",
                            "cathedral",
                            "duomo",
                            "catedral",
                            "catedrale",
                            "cathedrale",
                            "kathedrale"),
                    words(PlaceCategory.SACRAL, "basilica", "bazylika", "basilica"),
                    words(
                            PlaceCategory.SACRAL,
                            "chapel",
                            "kaplica",
                            "chapel",
                            "capilla",
                            "cappella",
                            "chapelle",
                            "kapelle"),
                    words(
                            PlaceCategory.SACRAL,
                            "mosque",
                            "meczet",
                            "mosque",
                            "masjid",
                            "mezquita",
                            "moschee",
                            "mosquee"),
                    words(PlaceCategory.SACRAL, "synagogue", "synagoga", "synagogue"),
                    words(
                            PlaceCategory.SACRAL,
                            "monastery",
                            "klasztor",
                            "monastery",
                            "opactwo",
                            "abbey",
                            "monasterio",
                            "monastero",
                            "monastere",
                            "kloster"),
                    words(PlaceCategory.SACRAL, "shrine", "shrine", "taisha", "jinja"),
                    words(
                            PlaceCategory.SACRAL,
                            "temple",
                            "swiatynia",
                            "temple",
                            "templo",
                            "tempio",
                            "wat",
                            "pagoda"),
                    words(PlaceCategory.SACRAL, "sanctuary", "sanktuarium", "sanctuary"),
                    suffixes(PlaceCategory.SACRAL, "church", "kirche", "kirkja"),
                    words(
                            PlaceCategory.SACRAL,
                            "church",
                            "kosciol",
                            "church",
                            "cerkiew",
                            "parafia",
                            "iglesia",
                            "chiesa",
                            "eglise",
                            "igreja",
                            "cerkev"),
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
                            "miradouro",
                            "aussichtspunkt",
                            "punto panoramico",
                            "panorama",
                            "observation deck",
                            "skydeck",
                            "vista point"),
                    words(
                            PlaceCategory.MOUNTAIN,
                            "glacier",
                            "lodowiec",
                            "glacier",
                            "glaciar",
                            "ghiacciaio",
                            "gletscher"),
                    words(
                            PlaceCategory.MOUNTAIN,
                            "volcano",
                            "wulkan",
                            "volcano",
                            "volcan",
                            "vulcano",
                            "vulkan"),
                    words(PlaceCategory.MOUNTAIN, "pass", "przelecz", "mountain pass", "alpenpass"),
                    words(
                            PlaceCategory.MOUNTAIN,
                            "canyon",
                            "kanion",
                            "canyon",
                            "wawoz",
                            "gorge",
                            "garganta",
                            "schlucht",
                            "gorges"),
                    words(
                            PlaceCategory.MOUNTAIN,
                            "cave",
                            "jaskinia",
                            "grota",
                            "cave",
                            "cueva",
                            "grotte",
                            "grotta",
                            "hohle"),
                    words(PlaceCategory.MOUNTAIN, "valley", "dolina", "valley", "valle", "vallee"),
                    words(PlaceCategory.MOUNTAIN, "cliff", "klif", "cliff"),
                    words(
                            PlaceCategory.MOUNTAIN,
                            "peak",
                            "szczyt",
                            "peak",
                            "mount",
                            "mt",
                            "turnia",
                            "cerro",
                            "pico",
                            "monte",
                            "montagna",
                            "montagne",
                            "gipfel",
                            "spitze",
                            "berg",
                            "pic du",
                            "pic de"),
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
                    words(
                            PlaceCategory.WATER,
                            "waterfall",
                            "wodospad",
                            "waterfall",
                            "falls",
                            "cascada",
                            "cascade",
                            "cascata",
                            "wasserfall"),
                    words(
                            PlaceCategory.WATER,
                            "river",
                            "rzeka",
                            "river",
                            "rio",
                            "fiume",
                            "fleuve",
                            "fluss"),
                    words(
                            PlaceCategory.WATER,
                            "lake",
                            "jezioro",
                            "lake",
                            "staw",
                            "lagoon",
                            "laguna",
                            "lago",
                            "lac",
                            "lagoa"),
                    rawWords(PlaceCategory.WATER, "beach", "pla\u017Ca"),
                    words(
                            PlaceCategory.WATER,
                            "beach",
                            "beach",
                            "playa",
                            "spiaggia",
                            "plage",
                            "praia",
                            "strand"),
                    words(PlaceCategory.WATER, "fjord", "fiord", "fjord"),
                    words(
                            PlaceCategory.WATER,
                            "hot_spring",
                            "zrodla termalne",
                            "termy",
                            "hot spring",
                            "hot springs",
                            "onsen",
                            "therme",
                            "thermen",
                            "terme",
                            "geyser",
                            "gejzer"),
                    words(PlaceCategory.WATER, "canal", "kanal", "canal"),
                    words(
                            PlaceCategory.NATURE_PARK,
                            "national_park",
                            "park narodowy",
                            "national park",
                            "nationalpark",
                            "parque nacional",
                            "parco nazionale",
                            "parc national"),
                    words(
                            PlaceCategory.NATURE_PARK,
                            "botanical_garden",
                            "ogrod botaniczny",
                            "botanical garden",
                            "arboretum",
                            "jardin botanico",
                            "jardin botanique",
                            "giardino botanico"),
                    words(
                            PlaceCategory.NATURE_PARK,
                            "nature_reserve",
                            "rezerwat",
                            "nature reserve",
                            "park krajobrazowy",
                            "state park",
                            "regional park",
                            "reserva natural",
                            "riserva naturale",
                            "reserve naturelle",
                            "naturschutzgebiet"),
                    words(
                            PlaceCategory.NATURE_PARK,
                            "forest",
                            "lasy",
                            "puszcza",
                            "forest",
                            "woods",
                            "bosque",
                            "foret",
                            "wald",
                            "bosco"),
                    words(
                            PlaceCategory.NATURE_PARK,
                            "park",
                            "park",
                            "ogrod",
                            "garden",
                            "parque",
                            "parco",
                            "parc",
                            "jardin",
                            "giardino",
                            "garten"),
                    words(PlaceCategory.RESTAURANT, "milk_bar", "bar mleczny"),
                    words(PlaceCategory.RESTAURANT, "pizzeria", "pizzeria"),
                    words(PlaceCategory.RESTAURANT, "bistro", "bistro"),
                    words(PlaceCategory.RESTAURANT, "ramen", "ramen"),
                    words(PlaceCategory.RESTAURANT, "sushi", "sushi", "izakaya"),
                    words(PlaceCategory.RESTAURANT, "steakhouse", "steakhouse", "churrascaria"),
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
                            "restaurante",
                            "ristorante",
                            "trattoria",
                            "osteria",
                            "brasserie",
                            "taqueria",
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
                            "cafeteria",
                            "coffee",
                            "espresso",
                            "roastery",
                            "palarnia kawy",
                            "kaffeehaus"),
                    words(
                            PlaceCategory.CAFE_BAR,
                            "bakery",
                            "cukiernia",
                            "piekarnia",
                            "bakery",
                            "patisserie",
                            "boulangerie",
                            "pasteleria",
                            "pasticceria",
                            "lodziarnia",
                            "gelateria",
                            "gelato"),
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
                    words(PlaceCategory.WATER, "bay", "zatoka", "bay", "cove"),
                    words(PlaceCategory.WATER, "island", "wyspa", "island"),
                    words(PlaceCategory.HOTEL, "hotel", "inn"),
                    words(
                            PlaceCategory.LANDMARK,
                            "castle",
                            "zamek",
                            "castle",
                            "chateau",
                            "schloss",
                            "castillo",
                            "castello",
                            "castelo",
                            "burg",
                            "festung",
                            "alcazar",
                            "twierdza",
                            "fortress",
                            "fort",
                            "cytadela",
                            "citadel"),
                    words(
                            PlaceCategory.LANDMARK,
                            "palace",
                            "palac",
                            "palace",
                            "palacio",
                            "palazzo",
                            "palais"),
                    words(
                            PlaceCategory.LANDMARK,
                            "bridge",
                            "most",
                            "bridge",
                            "wiadukt",
                            "viaduct",
                            "puente",
                            "ponte",
                            "pont",
                            "brucke"),
                    words(PlaceCategory.LANDMARK, "tower", "wieza", "tower", "torre", "turm"),
                    words(
                            PlaceCategory.LANDMARK,
                            "gate",
                            "brama",
                            "gate",
                            "arch",
                            "puerta",
                            "porta",
                            "porte"),
                    rawWords(PlaceCategory.LANDMARK, "square", "pra\u00E7a"),
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
                            "ruinas",
                            "ruine",
                            "ruines",
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
                    words(
                            PlaceCategory.LANDMARK,
                            "lighthouse",
                            "latarnia",
                            "lighthouse",
                            "faro",
                            "phare",
                            "leuchtturm"),
                    words(
                            PlaceCategory.LANDMARK,
                            "town_hall",
                            "ratusz",
                            "town hall",
                            "rathaus",
                            "ayuntamiento"),
                    words(
                            PlaceCategory.LANDMARK,
                            "old_town",
                            "stare miasto",
                            "old town",
                            "altstadt",
                            "casco antiguo",
                            "centro storico"),
                    words(
                            PlaceCategory.LANDMARK,
                            "market",
                            "market",
                            "mercado",
                            "mercato",
                            "marche",
                            "markt",
                            "bazar",
                            "bazaar"),
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
