package cn.sdh.backend.service;

import cn.sdh.backend.dto.EvaluationExperimentDetail;
import cn.sdh.backend.dto.EvaluationExperimentRequest;
import cn.sdh.backend.entity.EvaluationExperiment;
import cn.sdh.backend.entity.EvaluationExperimentRun;

import java.util.List;

public interface EvaluationExperimentService {
    List<EvaluationExperiment> list();
    EvaluationExperimentDetail detail(Long id);
    EvaluationExperiment create(EvaluationExperimentRequest request);
    void toggle(Long id);
    void delete(Long id);
    EvaluationExperimentRun runNow(Long id);
    void schedulerTick();
}
