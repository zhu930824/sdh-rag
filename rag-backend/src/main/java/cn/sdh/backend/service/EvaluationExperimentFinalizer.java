package cn.sdh.backend.service;

import cn.sdh.backend.entity.EvaluationExperiment;
import cn.sdh.backend.entity.EvaluationExperimentRun;
import cn.sdh.backend.mapper.EvaluationExperimentRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EvaluationExperimentFinalizer {
    private final EvaluationExperimentRunMapper runMapper;
    private final WebhookService webhookService;

    @Transactional(rollbackFor = Exception.class)
    public void finalizeRun(EvaluationExperiment experiment, EvaluationExperimentRun run,
                            String status, String error, boolean regression) {
        run.setStatus(status);
        run.setErrorMessage(cap(error));
        run.setCompleteTime(LocalDateTime.now());
        runMapper.updateById(run);

        if ("FAILED".equals(status)) {
            webhookService.triggerWebhooks("evaluation.run_failed", failurePayload(experiment, run, error),
                    "evaluation.run_failed:" + experiment.getId() + ":" + run.getId());
        } else if (regression) {
            webhookService.triggerWebhooks("evaluation.regression", Map.of(
                            "experimentId", experiment.getId(),
                            "experimentName", experiment.getName(),
                            "runId", run.getId(),
                            "message", "评测实验出现质量回归"),
                    "evaluation.regression:" + run.getId());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void publishSchedulingFailure(EvaluationExperiment experiment, String error) {
        String occurrence = experiment.getNextRunTime() == null
                ? String.valueOf(System.currentTimeMillis()) : experiment.getNextRunTime().toString();
        webhookService.triggerWebhooks("evaluation.run_failed", failurePayload(experiment, null, error),
                "evaluation.schedule_failed:" + experiment.getId() + ":" + occurrence);
    }

    private Map<String, Object> failurePayload(EvaluationExperiment experiment,
                                               EvaluationExperimentRun run, String error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("experimentId", experiment.getId());
        payload.put("experimentName", experiment.getName());
        if (run != null) payload.put("runId", run.getId());
        payload.put("message", Optional.ofNullable(error).orElse("评测实验执行失败"));
        return payload;
    }

    private String cap(String value) {
        return value == null ? null : value.substring(0, Math.min(1000, value.length()));
    }
}
