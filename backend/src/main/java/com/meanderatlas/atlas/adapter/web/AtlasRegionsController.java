package com.meanderatlas.atlas.adapter.web;

import com.meanderatlas.atlas.AtlasFacade;
import com.meanderatlas.atlas.RegionDossierView;
import com.meanderatlas.atlas.RegionFilterQuery;
import com.meanderatlas.atlas.RegionScope;
import com.meanderatlas.atlas.RegionSummaryView;
import com.meanderatlas.shared.DerivedRole;
import com.meanderatlas.shared.PlaceCategory;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/atlas/regions")
class AtlasRegionsController {

    private final AtlasFacade atlasFacade;

    AtlasRegionsController(AtlasFacade atlasFacade) {
        this.atlasFacade = atlasFacade;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    List<RegionSummaryView> regions(
            @RequestParam(defaultValue = "COUNTRY") RegionScope level,
            @RequestParam(required = false) @Nullable String countryCode,
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(required = false) @Nullable DerivedRole derivedRole,
            @RequestParam(required = false) @Nullable PlaceCategory category,
            @RequestParam(required = false) @Nullable String account,
            @RequestParam(required = false) @Nullable String list) {
        return atlasFacade.regionSummaries(
                level, countryCode, new RegionFilterQuery(q, derivedRole, category, account, list));
    }

    @GetMapping(value = "/{level}/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
    RegionDossierView regionDossier(@PathVariable RegionScope level, @PathVariable String code) {
        return atlasFacade.regionDossier(level, code);
    }
}
