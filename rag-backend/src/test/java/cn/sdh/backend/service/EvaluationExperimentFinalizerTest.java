package cn.sdh.backend.service;

import cn.sdh.backend.entity.EvaluationExperiment;
import cn.sdh.backend.entity.EvaluationExperimentRun;
import cn.sdh.backend.mapper.EvaluationExperimentRunMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationExperimentFinalizerTest {
    @Mock EvaluationExperimentRunMapper runMapper;
    @Mock WebhookService webhookService;

    @Test
    void failedRunUsesStableEventKeyAfterWritingTerminalState() {
        EvaluationExperimentFinalizer finalizer = new EvaluationExperimentFinalizer(runMapper, webhookService);
        EvaluationExperiment experiment = new EvaluationExperiment();
        experiment.setId(8L);
        experiment.setName("发布质量回归");
        EvaluationExperimentRun run = new EvaluationExperimentRun();
        run.setId(13L);

        finalizer.finalizeRun(experiment, run, "FAILED", "judge unavailable", false);

        assertEquals("FAILED", run.getStatus());
        InOrder order = inOrder(runMapper, webhookService);
        order.verify(runMapper).updateById(run);
        order.verify(webhookService).triggerWebhooks(eq("evaluation.run_failed"), anyMap(),
                eq("evaluation.run_failed:8:13"));
    }

    @Test
    void completedRegressionPublishesRegressionEvent() {
        EvaluationExperimentFinalizer finalizer = new EvaluationExperimentFinalizer(runMapper, webhookService);
        EvaluationExperiment experiment = new EvaluationExperiment();
        experiment.setId(8L);
        experiment.setName("发布质量回归");
        EvaluationExperimentRun run = new EvaluationExperimentRun();
        run.setId(13L);

        finalizer.finalizeRun(experiment, run, "COMPLETED", null, true);

        verify(webhookService).triggerWebhooks(eq("evaluation.regression"), anyMap(),
                eq("evaluation.regression:13"));
    }
}
