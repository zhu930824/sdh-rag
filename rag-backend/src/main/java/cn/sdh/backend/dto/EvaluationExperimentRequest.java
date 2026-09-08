package cn.sdh.backend.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class EvaluationExperimentRequest {
    private String name;
    private String description;
    private Long knowledgeId;
    private Long datasetId;
    private Boolean scheduleEnabled = false;
    private String cronExpression;
    private Integer judgeBudget = 0;
    private Integer maxRetries = 1;
    private Boolean alertOnRegression = true;
    private List<Variant> variants = new ArrayList<>();

    @Data
    public static class Variant {
        private String name;
        private String evaluationMode = "RETRIEVAL";
        private Integer topK = 5;
        private String judgeModel;
        private Long baselineTaskId;
    }
}
