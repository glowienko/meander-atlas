package com.meanderatlas.atlas;

import com.meanderatlas.shared.PlaceId;

public record UpsertedPlaceView(PlaceId placeId, boolean created) {}
