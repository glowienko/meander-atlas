package com.meanderatlas.atlas.adapter.persistence;

import com.meanderatlas.atlas.domain.AtlasHealthRepository;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAtlasHealthRepository implements AtlasHealthRepository {

    private static final String SELECT_POSTGIS_VERSION =
            """
            SELECT PostGIS_Lib_Version()
            """;
    private static final String MISSING_POSTGIS_VERSION_MESSAGE =
            "PostGIS extension version is unavailable";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcAtlasHealthRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String fetchPostgisVersion() {
        String version =
                jdbcTemplate.queryForObject(SELECT_POSTGIS_VERSION, Map.of(), String.class);
        if (version == null || version.isBlank()) {
            throw new IllegalStateException(MISSING_POSTGIS_VERSION_MESSAGE);
        }
        return version;
    }
}
