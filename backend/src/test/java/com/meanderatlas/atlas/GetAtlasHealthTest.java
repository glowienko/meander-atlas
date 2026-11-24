package com.meanderatlas.atlas;

import static org.assertj.core.api.Assertions.assertThat;

import com.meanderatlas.atlas.application.GetAtlasHealth;
import com.meanderatlas.atlas.domain.AtlasHealthRepository;
import org.junit.jupiter.api.Test;

class GetAtlasHealthTest {

    private static final String SAMPLE_POSTGIS_VERSION = "3.5.0";

    @Test
    void returnsUpHealthViewWithPostgisVersionFromRepository() {
        AtlasHealthRepository fakeRepository = () -> SAMPLE_POSTGIS_VERSION;
        GetAtlasHealth useCase = new GetAtlasHealth(fakeRepository);

        AtlasHealthView view = useCase.execute();

        assertThat(view.status()).isEqualTo(AtlasHealthView.STATUS_UP);
        assertThat(view.database()).isEqualTo(AtlasHealthView.DATABASE_POSTGRESQL);
        assertThat(view.postgisVersion()).isEqualTo(SAMPLE_POSTGIS_VERSION);
    }
}
