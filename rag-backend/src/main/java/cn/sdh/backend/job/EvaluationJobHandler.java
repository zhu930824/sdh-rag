package cn.sdh.backend.job;

import cn.sdh.backend.entity.EvaluationTask;
import cn.sdh.backend.entity.PlatformJob;
import cn.sdh.backend.service.EvaluationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EvaluationJobHandler implements PlatformJobHandler {
    public static final String TYPE = "RAG_EVALUATION";

    private final EvaluationService evaluationService;
    private final ObjectMapper objectMapper;

    @Override
    public String jobType() {
        return TYPE;
    }

    @Override
    public String execute(PlatformJob job, PlatformJobExecutionContext context) throws Exception {
        EvaluationJobPayload payload = objectMapper.readValue(job.getPayloadJson(), EvaluationJobPayload.class);
        if (payload.taskId() == null) throw new IllegalArgumentException("评测任务 ID 不能为空");
        if (EvaluationJobPayload.GENERATED.equals(payload.mode())) {
            evaluationService.runEvaluation(payload.taskId(), context);
        } else if (EvaluationJobPayload.DATASET.equals(payload.mode())) {
            evaluationService.runImportedEvaluation(payload.taskId(), payload.items(), context);
        } else {
            throw new IllegalArgumentException("未知评测任务模式: " + payload.mode());
        }
        EvaluationTask task = evaluationService.getTaskDetail(payload.taskId());
        if (task == null || !Integer.valueOf(2).equals(task.getStatus())) {
            throw new IllegalStateException(task == null ? "评测任务不存在" : task.getErrorMessage());
        }
        return "评测完成，共处理 " + task.getQaCount() + " 条样本";
    }
}
