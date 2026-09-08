package cn.sdh.backend.dto;

import java.math.BigDecimal;

public record EvaluationJudgeAlignment(
        Long taskId,
        int reviewedCount,
        BigDecimal faithfulnessMae,
        BigDecimal answerRelevancyMae,
        BigDecimal completenessMae,
        BigDecimal fluencyMae,
        BigDecimal overallMae,
        BigDecimal withinOneLevelRate) { }
