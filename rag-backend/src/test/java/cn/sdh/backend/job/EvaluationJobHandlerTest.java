package cn.sdh.backend.job;

import cn.sdh.backend.dto.EvaluationQaItem;
import cn.sdh.backend.entity.EvaluationTask;
import cn.sdh.backend.entity.PlatformJob;
import cn.sdh.backend.service.EvaluationService;
import cn.sdh.backend.service.PlatformJobService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class EvaluationJobHandlerTest {
    @Test void routesPersistedDatasetPayloadAndReturnsSummary() throws Exception {
        EvaluationService evaluations = mock(EvaluationService.class);
        PlatformJobService jobs = mock(PlatformJobService.class);
        EvaluationJobHandler handler = new EvaluationJobHandler(evaluations, new ObjectMapper());
        EvaluationQaItem item = new EvaluationQaItem();
        item.setQuestion("退款时效是什么？");
        EvaluationJobPayload payload = new EvaluationJobPayload(7L, EvaluationJobPayload.DATASET, List.of(item));
        PlatformJob job = new PlatformJob();
        job.setPayloadJson(new ObjectMapper().writeValueAsString(payload));
        EvaluationTask completed = new EvaluationTask();
        completed.setId(7L);
        completed.setStatus(2);
        completed.setQaCount(1);
        when(evaluations.getTaskDetail(7L)).thenReturn(completed);

        String summary = handler.execute(job, new PlatformJobExecutionContext(1L, "worker", jobs));

        assertEquals("评测完成，共处理 1 条样本", summary);
        verify(evaluations).runImportedEvaluation(eq(7L), anyList(), any());
    }
}
