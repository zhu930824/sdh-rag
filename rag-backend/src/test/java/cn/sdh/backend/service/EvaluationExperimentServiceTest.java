package cn.sdh.backend.service;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.dto.EvaluationExperimentRequest;
import cn.sdh.backend.entity.EvaluationExperiment;
import cn.sdh.backend.entity.EvaluationExperimentVariant;
import cn.sdh.backend.entity.TestDataset;
import cn.sdh.backend.mapper.*;
import cn.sdh.backend.service.impl.EvaluationExperimentServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationExperimentServiceTest {
    @Mock EvaluationExperimentMapper experiments;
    @Mock EvaluationExperimentVariantMapper variants;
    @Mock EvaluationExperimentRunMapper runs;
    @Mock EvaluationExperimentRunTaskMapper runTasks;
    @Mock EvaluationTaskMapper tasks;
    @Mock EvaluationService evaluationService;
    @Mock TestDatasetService datasetService;
    @Mock KnowledgeAccessService access;
    @Mock WebhookService webhooks;
    EvaluationExperimentServiceImpl service;

    @BeforeEach void setup() {
        UserContext context = new UserContext();
        context.setUserId(7L);
        UserContext.setUserContext(context);
        service = new EvaluationExperimentServiceImpl(experiments, variants, runs, runTasks, tasks,
                evaluationService, datasetService, access, webhooks);
    }

    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void createPersistsComparableVariantsAndCronSchedule() {
        TestDataset dataset = dataset(10, "builtin");
        when(datasetService.getDatasetDetail(3L)).thenReturn(dataset);
        doAnswer(invocation -> {
            EvaluationExperiment value = invocation.getArgument(0);
            value.setId(21L);
            return 1;
        }).when(experiments).insert(any(EvaluationExperiment.class));
        EvaluationExperimentRequest request = request();
        request.setScheduleEnabled(true);
        request.setCronExpression("0 0 2 * * *");

        EvaluationExperiment created = service.create(request);

        assertEquals(21L, created.getId());
        assertNotNull(created.getNextRunTime());
        assertEquals("ACTIVE", created.getStatus());
        verify(variants, times(2)).insert(any(EvaluationExperimentVariant.class));
        verify(access).requireEdit(9L, 7L);
    }

    @Test void runRejectsJudgeCallsAboveBudgetBeforeCreatingBatch() {
        EvaluationExperiment experiment = new EvaluationExperiment();
        experiment.setId(21L);
        experiment.setName("生成配置对比");
        experiment.setKnowledgeId(9L);
        experiment.setDatasetId(3L);
        experiment.setUserId(7L);
        experiment.setJudgeBudget(5);
        when(experiments.selectById(21L)).thenReturn(experiment);
        when(datasetService.getDatasetDetail(3L)).thenReturn(dataset(10, "builtin"));
        EvaluationExperimentVariant first = variant(1L, "FULL");
        EvaluationExperimentVariant second = variant(2L, "GENERATION");
        when(variants.selectList(any())).thenReturn(List.of(first, second));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.runNow(21L));

        assertTrue(error.getMessage().contains("超过单次预算"));
        verify(runs, never()).insert(any(cn.sdh.backend.entity.EvaluationExperimentRun.class));
    }

    private EvaluationExperimentRequest request() {
        EvaluationExperimentRequest request = new EvaluationExperimentRequest();
        request.setName("Top K 对比");
        request.setKnowledgeId(9L);
        request.setDatasetId(3L);
        EvaluationExperimentRequest.Variant a = new EvaluationExperimentRequest.Variant();
        a.setName("Top 5");
        a.setTopK(5);
        EvaluationExperimentRequest.Variant b = new EvaluationExperimentRequest.Variant();
        b.setName("Top 10");
        b.setTopK(10);
        request.setVariants(List.of(a, b));
        return request;
    }

    private TestDataset dataset(int itemCount, String type) {
        TestDataset dataset = new TestDataset();
        dataset.setId(3L);
        dataset.setItemCount(itemCount);
        dataset.setDatasetType(type);
        dataset.setUserId(7L);
        return dataset;
    }

    private EvaluationExperimentVariant variant(Long id, String mode) {
        EvaluationExperimentVariant variant = new EvaluationExperimentVariant();
        variant.setId(id);
        variant.setExperimentId(21L);
        variant.setName(mode);
        variant.setEvaluationMode(mode);
        variant.setTopK(5);
        return variant;
    }
}
