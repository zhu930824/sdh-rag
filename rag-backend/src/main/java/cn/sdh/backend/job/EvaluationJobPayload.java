package cn.sdh.backend.job;

import cn.sdh.backend.dto.EvaluationQaItem;

import java.util.List;

public record EvaluationJobPayload(Long taskId, String mode, List<EvaluationQaItem> items) {
    public static final String REPLAY = "REPLAY";
    public static final String GENERATED = "GENERATED";
    public static final String DATASET = "DATASET";
}
