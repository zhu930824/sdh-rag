package cn.sdh.backend.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RagEvaluationMetricsTest {
    @Test void calculatesRecallPrecisionMrrNdcgAndMapFromGradedLabels() {
        Map<Long, Integer> labels = new LinkedHashMap<>();
        labels.put(1L, 3);
        labels.put(2L, 2);
        labels.put(5L, 1);

        var result = RagEvaluationMetrics.calculate(List.of(2L, 3L, 1L, 4L), labels, 3);

        assertEquals(2d / 3d, result.recall(), 0.0001);
        assertEquals(2d / 3d, result.precision(), 0.0001);
        assertEquals(1d, result.reciprocalRank(), 0.0001);
        assertEquals(0.7350, result.ndcg(), 0.001);
        assertEquals(0.5556, result.averagePrecision(), 0.001);
        assertEquals(1, result.firstRank());
    }

    @Test void emptyGroundTruthIsNotReportedAsAHit() {
        var result = RagEvaluationMetrics.calculate(List.of(1L), Map.of(), 5);
        assertFalse(result.hit());
        assertNull(result.firstRank());
        assertEquals(0, result.recall());
    }

    @Test void missingIdsAndDuplicateDocumentsKeepTheirOriginalRank() {
        var result = RagEvaluationMetrics.calculate(
                Arrays.asList(null, 1L, 1L), Map.of(1L, 3), 3);

        assertEquals(1, result.recall());
        assertEquals(1d / 3d, result.precision(), 0.0001);
        assertEquals(0.5, result.reciprocalRank(), 0.0001);
        assertEquals(0.6309, result.ndcg(), 0.001);
        assertEquals(0.5, result.averagePrecision(), 0.0001);
        assertEquals(2, result.firstRank());
    }
}
