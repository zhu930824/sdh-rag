package cn.sdh.backend.knowledge.connector;

import cn.sdh.backend.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RestJsonKnowledgeSourceConnectorTest {
    private final RestJsonKnowledgeSourceConnector connector = new RestJsonKnowledgeSourceConnector(new ObjectMapper());

    @Test void secureConfigurationIsAcceptedAndPlainHttpRequiresExplicitOptIn() {
        assertDoesNotThrow(() -> connector.validate(Map.of("endpoint", "https://business.example/knowledge")));
        assertThrows(BusinessException.class,
                () -> connector.validate(Map.of("endpoint", "http://business.example/knowledge")));
        assertDoesNotThrow(() -> connector.validate(Map.of(
                "endpoint", "http://business.example/knowledge", "allowInsecureHttp", true)));
    }

    @Test void responseContractParsesIncrementalItems() throws Exception {
        String json = """
                {"items":[{"externalId":"faq-1","title":"退款","content":"七天内可退款",
                "sourceUpdatedAt":"2026-09-07T07:00:00Z","metadata":{"department":"客服"}}],
                "nextCursor":"cursor-2","hasMore":true}
                """;

        var result = connector.parseResponse(json.getBytes(StandardCharsets.UTF_8), 100);

        assertEquals(1, result.items().size());
        assertEquals("faq-1", result.items().getFirst().externalId());
        assertEquals("客服", result.items().getFirst().metadata().get("department"));
        assertEquals("cursor-2", result.nextCursor());
        assertTrue(result.hasMore());
    }

    @Test void oversizedPageAndMalformedTimestampAreRejected() {
        String twoItems = "{\"items\":[{\"externalId\":\"1\"},{\"externalId\":\"2\"}]}";
        assertThrows(BusinessException.class,
                () -> connector.parseResponse(twoItems.getBytes(StandardCharsets.UTF_8), 1));
        String badTime = "{\"items\":[{\"externalId\":\"1\",\"sourceUpdatedAt\":\"yesterday\"}]}";
        assertThrows(BusinessException.class,
                () -> connector.parseResponse(badTime.getBytes(StandardCharsets.UTF_8), 10));
    }
}
