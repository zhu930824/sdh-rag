package cn.sdh.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RagEvaluationDatasetTest {
    @Test void mvpDatasetContainsUsablePositiveAndNegativeCases() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/datasets/rag_evaluation_mvp.json")) {
            assertNotNull(input);
            Map<String, Object> dataset = new ObjectMapper().readValue(input, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) dataset.get("items");

            assertEquals(24, items.size());
            assertEquals(4, items.stream().filter(item -> Boolean.TRUE.equals(item.get("isNegative"))).count());
            assertTrue(items.stream().allMatch(item -> item.get("question") instanceof String question && !question.isBlank()));
            assertTrue(items.stream().allMatch(item -> item.get("expectedAnswer") instanceof String answer && !answer.isBlank()));
            assertTrue(items.stream().allMatch(item -> item.containsKey("category") && item.containsKey("difficulty")));
        }
    }
}
