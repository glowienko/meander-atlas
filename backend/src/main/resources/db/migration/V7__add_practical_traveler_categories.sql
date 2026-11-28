ALTER TABLE places DROP CONSTRAINT IF EXISTS places_category_check;
ALTER TABLE places
    ADD CONSTRAINT places_category_check
    CHECK (category IN (
        'RESTAURANT',
        'CAFE_BAR',
        'HOTEL',
        'MUSEUM',
        'LANDMARK',
        'SACRAL',
        'MOUNTAIN',
        'WATER',
        'NATURE_PARK',
        'VIEWPOINT',
        'GROCERIES',
        'PHARMACY',
        'RESTROOM',
        'TRANSPORT',
        'PARKING',
        'SHOPPING',
        'UNCATEGORIZED'
    ));
