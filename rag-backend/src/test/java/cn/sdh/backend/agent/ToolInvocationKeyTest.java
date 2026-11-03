package cn.sdh.backend.agent;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ToolInvocationKeyTest {
    @Test void equivalentParameterOrderReusesTheSameBusinessOperation() {
        var first = new LinkedHashMap<String, Object>(); first.put("b", 2); first.put("a", Map.of("z", 3, "x", 4));
        var second = new LinkedHashMap<String, Object>(); second.put("a", Map.of("x", 4, "z", 3)); second.put("b", 2);
        assertEquals(ToolInvocationKey.create("run", "ticket", first), ToolInvocationKey.create("run", "ticket", second));
        assertNotEquals(ToolInvocationKey.create("run", "ticket", first), ToolInvocationKey.create("other-run", "ticket", first));
    }
    @Test void maximumLengthToolNamesStillProduceAnAcceptedIdempotencyKey() {
        String key = ToolInvocationKey.create("r".repeat(32), "t".repeat(80), Map.of("orderId", "42"));
        assertTrue(key.length() <= 128);
        assertTrue(key.matches("[A-Za-z0-9][A-Za-z0-9._:-]*"));
    }
}
