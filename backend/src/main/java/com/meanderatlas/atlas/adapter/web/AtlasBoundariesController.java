package com.meanderatlas.atlas.adapter.web;

import com.meanderatlas.atlas.AtlasFacade;
import com.meanderatlas.atlas.BoundarySeedResultView;
import com.meanderatlas.atlas.RegionScope;
import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/atlas")
class AtlasBoundariesController {

    private static final MediaType MVT_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.mapbox-vector-tile");
    private static final Duration TILE_CACHE_DURATION = Duration.ofHours(1);

    private final AtlasFacade atlasFacade;

    AtlasBoundariesController(AtlasFacade atlasFacade) {
        this.atlasFacade = atlasFacade;
    }

    @PostMapping(value = "/boundaries/seed", produces = MediaType.APPLICATION_JSON_VALUE)
    BoundarySeedResultView seedBoundaries(@RequestParam(defaultValue = "false") boolean force) {
        return atlasFacade.seedBoundaries(force);
    }

    @GetMapping(
            value = "/tiles/regions/{level}/{z}/{x}/{y}.mvt",
            produces = "application/vnd.mapbox-vector-tile")
    ResponseEntity<byte[]> regionTile(
            @PathVariable RegionScope level,
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y,
            @RequestParam(required = false) @Nullable String countryCode) {
        byte[] tile = atlasFacade.regionMvtTile(level, z, x, y, countryCode);
        if (tile.length == 0) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok()
                .contentType(MVT_MEDIA_TYPE)
                .cacheControl(CacheControl.maxAge(TILE_CACHE_DURATION).cachePublic())
                .body(tile);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalidArguments(IllegalArgumentException ex) {
        String detail =
                Objects.requireNonNullElse(ex.getMessage(), "Invalid boundary tile request");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }
}
