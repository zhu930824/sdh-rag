package cn.sdh.backend.dto;

import java.math.BigDecimal;

public record EvaluationSliceMetric(
        String dimension,
        String value,
        int sampleCount,
        int failureCount,
        BigDecimal recallAtK,
        BigDecimal mrr,
        BigDecimal faithfulness,
        BigDecimal answerRelevancy,
        BigDecimal overallScore) { }
