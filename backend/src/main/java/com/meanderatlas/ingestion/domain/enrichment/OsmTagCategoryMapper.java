package com.meanderatlas.ingestion.domain.enrichment;

import com.meanderatlas.shared.PlaceCategory;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class OsmTagCategoryMapper {

    private static final Set<String> HOTEL_TOURISM =
            Set.of(
                    "hotel",
                    "hostel",
                    "motel",
                    "guest_house",
                    "chalet",
                    "alpine_hut",
                    "wilderness_hut",
                    "camp_site",
                    "caravan_site",
                    "apartment",
                    "resort");
    private static final Set<String> RESTAURANT_AMENITY =
            Set.of("restaurant", "fast_food", "food_court");
    private static final Set<String> CAFE_BAR_AMENITY =
            Set.of("cafe", "bar", "pub", "ice_cream", "coffee_shop", "biergarten", "nightclub");
    private static final Set<String> LANDMARK_AMENITY =
            Set.of("theatre", "arts_centre", "cinema", "opera_house", "marketplace", "fountain");
    private static final Set<String> PHARMACY_AMENITY =
            Set.of("pharmacy", "clinic", "hospital", "doctors");
    private static final Set<String> PARKING_AMENITY =
            Set.of("parking", "parking_entrance", "bicycle_parking", "parking_space");
    private static final Set<String> TRANSPORT_AMENITY =
            Set.of("bus_station", "ferry_terminal", "taxi");
    private static final Set<String> SACRAL_HISTORIC =
            Set.of(
                    "church",
                    "chapel",
                    "cathedral",
                    "monastery",
                    "mosque",
                    "synagogue",
                    "temple",
                    "shrine",
                    "wayside_shrine");
    private static final Set<String> LANDMARK_HISTORIC =
            Set.of(
                    "castle",
                    "palace",
                    "fort",
                    "fortress",
                    "citadel",
                    "ruins",
                    "monument",
                    "memorial",
                    "archaeological_site",
                    "manor",
                    "city_gate",
                    "tower",
                    "lighthouse",
                    "bridge");
    private static final Set<String> MOUNTAIN_NATURAL =
            Set.of(
                    "peak",
                    "volcano",
                    "saddle",
                    "ridge",
                    "cliff",
                    "cave_entrance",
                    "glacier",
                    "valley",
                    "gorge",
                    "canyon");
    private static final Set<String> WATER_NATURAL =
            Set.of(
                    "water",
                    "lake",
                    "lagoon",
                    "waterfall",
                    "hot_spring",
                    "geyser",
                    "spring",
                    "beach",
                    "bay",
                    "fjord",
                    "cape",
                    "island");
    private static final Set<String> NATURE_PARK_NATURAL =
            Set.of("wood", "forest", "national_park", "heath", "wetland");
    private static final Set<String> WATER_WATERWAY =
            Set.of("waterfall", "river", "canal", "rapids");
    private static final Set<String> NATURE_PARK_LEISURE =
            Set.of("park", "garden", "nature_reserve", "botanical_garden", "arboretum");
    private static final Set<String> WATER_LEISURE =
            Set.of("marina", "swimming_area", "beach_resort");
    private static final Set<String> ATTRACTION_TOURISM =
            Set.of("attraction", "artwork", "theme_park", "zoo", "aquarium");
    private static final Set<String> CAFE_BAR_CRAFT =
            Set.of("brewery", "winery", "distillery", "bakery");
    private static final Set<String> CAFE_BAR_SHOP =
            Set.of(
                    "bakery",
                    "pastry",
                    "confectionery",
                    "coffee",
                    "tea",
                    "wine",
                    "ice_cream",
                    "chocolate",
                    "deli");
    private static final Set<String> RESTAURANT_SHOP = Set.of("seafood", "cheese");
    private static final Set<String> GROCERIES_SHOP =
            Set.of("supermarket", "convenience", "greengrocer", "grocery", "butcher", "farm");
    private static final Set<String> SHOPPING_SHOP =
            Set.of(
                    "mall",
                    "department_store",
                    "clothes",
                    "outdoor",
                    "sports",
                    "books",
                    "antiques",
                    "gift",
                    "souvenir",
                    "electronics",
                    "shoes",
                    "hardware",
                    "variety_store",
                    "general");
    private static final Set<String> TRANSPORT_RAILWAY =
            Set.of("station", "halt", "tram_stop", "subway_entrance");
    private static final Set<String> TRANSPORT_PUBLIC =
            Set.of("station", "stop_position", "platform");
    private static final Set<String> TRANSPORT_AEROWAY = Set.of("aerodrome", "terminal");
    private static final Set<String> LANDMARK_MAN_MADE =
            Set.of("lighthouse", "tower", "observation_tower", "bridge", "pier", "windmill");
    private static final Set<String> LANDMARK_BUILDING =
            Set.of("castle", "palace", "civic", "townhall");

    private OsmTagCategoryMapper() {}

    public static OsmCategoryMapping map(Map<String, String> tags) {
        if (tags.isEmpty()) {
            return OsmCategoryMapping.UNCATEGORIZED;
        }
        return mapSpecificTourism(tags)
                .or(() -> mapAmenity(tags))
                .or(() -> mapHistoric(tags))
                .or(() -> mapNatural(tags))
                .or(() -> mapWaterway(tags))
                .or(() -> mapLeisure(tags))
                .or(() -> mapBoundary(tags))
                .or(() -> mapAttractionTourism(tags))
                .or(() -> mapCraftOrShop(tags))
                .or(() -> mapManMadeOrTransit(tags))
                .or(() -> mapBuilding(tags))
                .orElse(OsmCategoryMapping.UNCATEGORIZED);
    }

    private static Optional<OsmCategoryMapping> mapSpecificTourism(Map<String, String> tags) {
        String tourism = normalizeValue(tags.get("tourism"));
        if (tourism == null) {
            return Optional.empty();
        }
        if ("viewpoint".equals(tourism)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.VIEWPOINT, tourism));
        }
        if ("museum".equals(tourism) || "gallery".equals(tourism)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.MUSEUM, tourism));
        }
        if (HOTEL_TOURISM.contains(tourism)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.HOTEL, tourism));
        }
        return Optional.empty();
    }

    private static Optional<OsmCategoryMapping> mapAmenity(Map<String, String> tags) {
        String amenity = normalizeValue(tags.get("amenity"));
        if (amenity == null) {
            return Optional.empty();
        }
        if (RESTAURANT_AMENITY.contains(amenity)) {
            String cuisine = normalizeValue(tags.get("cuisine"));
            return Optional.of(
                    new OsmCategoryMapping(
                            PlaceCategory.RESTAURANT, cuisine != null ? cuisine : amenity));
        }
        if (CAFE_BAR_AMENITY.contains(amenity)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.CAFE_BAR, amenity));
        }
        if ("place_of_worship".equals(amenity) || "monastery".equals(amenity)) {
            String religion = normalizeValue(tags.get("religion"));
            return Optional.of(
                    new OsmCategoryMapping(
                            PlaceCategory.SACRAL, religion != null ? religion : amenity));
        }
        if (LANDMARK_AMENITY.contains(amenity)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.LANDMARK, amenity));
        }
        if ("planetarium".equals(amenity)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.MUSEUM, amenity));
        }
        return mapPracticalAmenity(amenity);
    }

    private static Optional<OsmCategoryMapping> mapPracticalAmenity(String amenity) {
        if (PHARMACY_AMENITY.contains(amenity)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.PHARMACY, amenity));
        }
        if ("toilets".equals(amenity)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.RESTROOM, amenity));
        }
        if (PARKING_AMENITY.contains(amenity)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.PARKING, amenity));
        }
        if (TRANSPORT_AMENITY.contains(amenity)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.TRANSPORT, amenity));
        }
        return Optional.empty();
    }

    private static Optional<OsmCategoryMapping> mapHistoric(Map<String, String> tags) {
        String historic = normalizeValue(tags.get("historic"));
        if (historic == null) {
            return Optional.empty();
        }
        if (SACRAL_HISTORIC.contains(historic)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.SACRAL, historic));
        }
        if (LANDMARK_HISTORIC.contains(historic)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.LANDMARK, historic));
        }
        return Optional.empty();
    }

    private static Optional<OsmCategoryMapping> mapNatural(Map<String, String> tags) {
        String natural = normalizeValue(tags.get("natural"));
        if (natural == null) {
            return Optional.empty();
        }
        if (MOUNTAIN_NATURAL.contains(natural)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.MOUNTAIN, natural));
        }
        if (WATER_NATURAL.contains(natural)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.WATER, natural));
        }
        if (NATURE_PARK_NATURAL.contains(natural)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.NATURE_PARK, natural));
        }
        return Optional.empty();
    }

    private static Optional<OsmCategoryMapping> mapWaterway(Map<String, String> tags) {
        String waterway = normalizeValue(tags.get("waterway"));
        if (waterway != null && WATER_WATERWAY.contains(waterway)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.WATER, waterway));
        }
        return Optional.empty();
    }

    private static Optional<OsmCategoryMapping> mapLeisure(Map<String, String> tags) {
        String leisure = normalizeValue(tags.get("leisure"));
        if (leisure == null) {
            return Optional.empty();
        }
        if (NATURE_PARK_LEISURE.contains(leisure)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.NATURE_PARK, leisure));
        }
        if (WATER_LEISURE.contains(leisure)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.WATER, leisure));
        }
        if ("stadium".equals(leisure)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.LANDMARK, leisure));
        }
        return Optional.empty();
    }

    private static Optional<OsmCategoryMapping> mapBoundary(Map<String, String> tags) {
        String boundary = normalizeValue(tags.get("boundary"));
        if ("national_park".equals(boundary) || "protected_area".equals(boundary)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.NATURE_PARK, boundary));
        }
        return Optional.empty();
    }

    private static Optional<OsmCategoryMapping> mapAttractionTourism(Map<String, String> tags) {
        String tourism = normalizeValue(tags.get("tourism"));
        if (tourism != null && ATTRACTION_TOURISM.contains(tourism)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.LANDMARK, tourism));
        }
        return Optional.empty();
    }

    private static Optional<OsmCategoryMapping> mapCraftOrShop(Map<String, String> tags) {
        String craft = normalizeValue(tags.get("craft"));
        if (craft != null && CAFE_BAR_CRAFT.contains(craft)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.CAFE_BAR, craft));
        }
        String shop = normalizeValue(tags.get("shop"));
        if (shop == null) {
            return Optional.empty();
        }
        if (GROCERIES_SHOP.contains(shop)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.GROCERIES, shop));
        }
        if (CAFE_BAR_SHOP.contains(shop)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.CAFE_BAR, shop));
        }
        if (RESTAURANT_SHOP.contains(shop)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.RESTAURANT, shop));
        }
        if (SHOPPING_SHOP.contains(shop)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.SHOPPING, shop));
        }
        return Optional.empty();
    }

    private static Optional<OsmCategoryMapping> mapManMadeOrTransit(Map<String, String> tags) {
        String manMade = normalizeValue(tags.get("man_made"));
        if (manMade != null && LANDMARK_MAN_MADE.contains(manMade)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.LANDMARK, manMade));
        }
        return mapTransit(tags);
    }

    private static Optional<OsmCategoryMapping> mapTransit(Map<String, String> tags) {
        String railway = normalizeValue(tags.get("railway"));
        if (railway != null && TRANSPORT_RAILWAY.contains(railway)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.TRANSPORT, railway));
        }
        String publicTransport = normalizeValue(tags.get("public_transport"));
        if (publicTransport != null && TRANSPORT_PUBLIC.contains(publicTransport)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.TRANSPORT, publicTransport));
        }
        String aeroway = normalizeValue(tags.get("aeroway"));
        if (aeroway != null && TRANSPORT_AEROWAY.contains(aeroway)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.TRANSPORT, aeroway));
        }
        String aerialway = normalizeValue(tags.get("aerialway"));
        if ("station".equals(aerialway)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.TRANSPORT, "cable_car"));
        }
        return Optional.empty();
    }

    private static Optional<OsmCategoryMapping> mapBuilding(Map<String, String> tags) {
        String building = normalizeValue(tags.get("building"));
        if (building == null || "yes".equals(building)) {
            return Optional.empty();
        }
        if (SACRAL_HISTORIC.contains(building)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.SACRAL, building));
        }
        if (LANDMARK_BUILDING.contains(building)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.LANDMARK, building));
        }
        if ("museum".equals(building)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.MUSEUM, building));
        }
        if ("hotel".equals(building)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.HOTEL, building));
        }
        if ("train_station".equals(building)) {
            return Optional.of(new OsmCategoryMapping(PlaceCategory.TRANSPORT, building));
        }
        return Optional.empty();
    }

    private static @Nullable String normalizeValue(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int semicolon = trimmed.indexOf(';');
        int comma = trimmed.indexOf(',');
        int end = trimmed.length();
        if (semicolon >= 0) {
            end = Math.min(end, semicolon);
        }
        if (comma >= 0) {
            end = Math.min(end, comma);
        }
        String first =
                trimmed.substring(0, end)
                        .trim()
                        .toLowerCase(Locale.ROOT)
                        .replace(' ', '_')
                        .replace('-', '_');
        return first.isEmpty() ? null : first;
    }

    public record OsmCategoryMapping(PlaceCategory category, @Nullable String subcategory) {
        public static final OsmCategoryMapping UNCATEGORIZED =
                new OsmCategoryMapping(PlaceCategory.UNCATEGORIZED, null);
    }
}
