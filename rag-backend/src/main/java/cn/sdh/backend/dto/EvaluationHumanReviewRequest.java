package cn.sdh.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EvaluationHumanReviewRequest {
    private String status = "COMPLETED";
    private BigDecimal faithfulness;
    private BigDecimal answerRelevancy;
    private BigDecimal completeness;
    private BigDecimal fluency;
    private BigDecimal overallScore;
    private String note;
}
