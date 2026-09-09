package cn.sdh.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.ai.dashscope.api-key=test-key",
    "spring.ai.openai.api-key=test-key"
})
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class RagBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
