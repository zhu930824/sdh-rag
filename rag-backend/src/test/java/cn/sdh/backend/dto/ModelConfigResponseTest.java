package cn.sdh.backend.dto;

import cn.sdh.backend.entity.ModelConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelConfigResponseTest {

    @Test
    void neverExposesAnyApiKeyFragment() {
        ModelConfig config = new ModelConfig();
        config.setApiKey("sk-sensitive-prefix-and-suffix");

        ModelConfigResponse response = ModelConfigResponse.fromEntity(config);

        assertTrue(response.getHasApiKey());
        assertEquals("********", response.getApiKeyMasked());
    }
}
