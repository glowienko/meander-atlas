package com.meanderatlas.ingestion.domain.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import com.meanderatlas.ingestion.domain.enrichment.OsmTagCategoryMapper.OsmCategoryMapping;
import com.meanderatlas.shared.PlaceCategory;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OsmTagCategoryMapperTest {

    @Test
    void mapsAllTenCategoriesAndNormalizesSubcategories() {
        assertThat(
                        OsmTagCategoryMapper.map(
                                Map.of("amenity", "restaurant", "cuisine", "Polish;European")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.RESTAURANT, "polish"));
        assertThat(OsmTagCategoryMapper.map(Map.of("amenity", "fast_food")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.RESTAURANT, "fast_food"));
        assertThat(OsmTagCategoryMapper.map(Map.of("amenity", "cafe")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.CAFE_BAR, "cafe"));
        assertThat(OsmTagCategoryMapper.map(Map.of("tourism", "alpine_hut")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.HOTEL, "alpine_hut"));
        assertThat(OsmTagCategoryMapper.map(Map.of("tourism", "museum")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.MUSEUM, "museum"));
        assertThat(OsmTagCategoryMapper.map(Map.of("amenity", "planetarium")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.MUSEUM, "planetarium"));
        assertThat(OsmTagCategoryMapper.map(Map.of("historic", "castle")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.LANDMARK, "castle"));
        assertThat(
                        OsmTagCategoryMapper.map(
                                Map.of("amenity", "place_of_worship", "religion", "christian")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.SACRAL, "christian"));
        assertThat(OsmTagCategoryMapper.map(Map.of("natural", "peak")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.MOUNTAIN, "peak"));
        assertThat(OsmTagCategoryMapper.map(Map.of("natural", "hot_spring")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.WATER, "hot_spring"));
        assertThat(OsmTagCategoryMapper.map(Map.of("leisure", "botanical-garden")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.NATURE_PARK, "botanical_garden"));
        assertThat(OsmTagCategoryMapper.map(Map.of("tourism", "viewpoint")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.VIEWPOINT, "viewpoint"));
    }

    @Test
    void enforcesTagPriorityAndRejectsGenericBuildingYes() {
        assertThat(OsmTagCategoryMapper.map(Map.of("tourism", "museum", "building", "yes")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.MUSEUM, "museum"));
        assertThat(OsmTagCategoryMapper.map(Map.of("historic", "castle", "tourism", "attraction")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.LANDMARK, "castle"));
        assertThat(OsmTagCategoryMapper.map(Map.of("building", "yes")))
                .isEqualTo(OsmCategoryMapping.UNCATEGORIZED);
        assertThat(OsmTagCategoryMapper.map(Map.of())).isEqualTo(OsmCategoryMapping.UNCATEGORIZED);
    }

    @Test
    void mapsSecondaryWaterwayBoundaryCraftShopManMadeAndBuildingTags() {
        assertThat(OsmTagCategoryMapper.map(Map.of("waterway", "waterfall")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.WATER, "waterfall"));
        assertThat(OsmTagCategoryMapper.map(Map.of("leisure", "marina")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.WATER, "marina"));
        assertThat(OsmTagCategoryMapper.map(Map.of("leisure", "stadium")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.LANDMARK, "stadium"));
        assertThat(OsmTagCategoryMapper.map(Map.of("boundary", "national_park")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.NATURE_PARK, "national_park"));
        assertThat(OsmTagCategoryMapper.map(Map.of("tourism", "attraction")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.LANDMARK, "attraction"));
        assertThat(OsmTagCategoryMapper.map(Map.of("craft", "brewery")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.CAFE_BAR, "brewery"));
        assertThat(OsmTagCategoryMapper.map(Map.of("shop", "bakery")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.CAFE_BAR, "bakery"));
        assertThat(OsmTagCategoryMapper.map(Map.of("shop", "seafood")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.RESTAURANT, "seafood"));
        assertThat(OsmTagCategoryMapper.map(Map.of("shop", "supermarket")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.GROCERIES, "supermarket"));
        assertThat(OsmTagCategoryMapper.map(Map.of("shop", "convenience")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.GROCERIES, "convenience"));
        assertThat(OsmTagCategoryMapper.map(Map.of("shop", "books")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.SHOPPING, "books"));
        assertThat(OsmTagCategoryMapper.map(Map.of("amenity", "pharmacy")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.PHARMACY, "pharmacy"));
        assertThat(OsmTagCategoryMapper.map(Map.of("amenity", "toilets")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.RESTROOM, "toilets"));
        assertThat(OsmTagCategoryMapper.map(Map.of("amenity", "parking")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.PARKING, "parking"));
        assertThat(OsmTagCategoryMapper.map(Map.of("amenity", "bus_station")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.TRANSPORT, "bus_station"));
        assertThat(OsmTagCategoryMapper.map(Map.of("man_made", "lighthouse")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.LANDMARK, "lighthouse"));
        assertThat(OsmTagCategoryMapper.map(Map.of("railway", "station")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.TRANSPORT, "station"));
        assertThat(OsmTagCategoryMapper.map(Map.of("public_transport", "station")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.TRANSPORT, "station"));
        assertThat(OsmTagCategoryMapper.map(Map.of("aerialway", "station")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.TRANSPORT, "cable_car"));
        assertThat(OsmTagCategoryMapper.map(Map.of("aeroway", "aerodrome")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.TRANSPORT, "aerodrome"));
        assertThat(OsmTagCategoryMapper.map(Map.of("building", "train_station")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.TRANSPORT, "train_station"));
        assertThat(OsmTagCategoryMapper.map(Map.of("building", "cathedral")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.SACRAL, "cathedral"));
        assertThat(OsmTagCategoryMapper.map(Map.of("building", "palace")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.LANDMARK, "palace"));
        assertThat(OsmTagCategoryMapper.map(Map.of("building", "museum")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.MUSEUM, "museum"));
        assertThat(OsmTagCategoryMapper.map(Map.of("building", "hotel")))
                .isEqualTo(new OsmCategoryMapping(PlaceCategory.HOTEL, "hotel"));
    }
}
