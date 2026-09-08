package cn.sdh.backend.dto;

import cn.sdh.backend.entity.EvaluationExperiment;
import cn.sdh.backend.entity.EvaluationExperimentRun;
import cn.sdh.backend.entity.EvaluationExperimentVariant;
import cn.sdh.backend.entity.EvaluationTask;

import java.util.List;

public record EvaluationExperimentDetail(
        EvaluationExperiment experiment,
        List<EvaluationExperimentVariant> variants,
        List<RunView> runs) {

    public record RunView(EvaluationExperimentRun run, List<VariantResult> results) { }

    public record VariantResult(
            EvaluationExperimentVariant variant,
            EvaluationTask task,
            Integer attempt) { }
}
