package com.meanderatlas.atlas;

import static org.assertj.core.api.Assertions.assertThat;

import com.meanderatlas.atlas.application.GetAtlasHealth;
import com.meanderatlas.atlas.domain.AtlasHealthRepository;
import org.junit.jupiter.api.Test;

class GetAtlasHealthTest {

    @Test
    void returnsUpHealthViewWithPostgisVersionFromRepository() {
        AtlasHealthRepository fakeRepository = () -> "3.5.0";
        GetAtlasHealth useCase = new GetAtlasHealth(fakeRepository);

        AtlasHealthView view = useCase.execute();

        assertThat(view.status()).isEqualTo("UP");
        assertThat(view.database()).isEqualTo("postgresql");
        assertThat(view.postgisVersion()).isEqualTo("3.5.0");
    }
}
