package cn.sdh.backend.dto;

import lombok.Data;

@Data
public class EvaluationRunConfig {
    private String evaluationMode = "RETRIEVAL";
    private Integer topK = 5;
    private String judgeModel;
    private Long baselineTaskId;
}
