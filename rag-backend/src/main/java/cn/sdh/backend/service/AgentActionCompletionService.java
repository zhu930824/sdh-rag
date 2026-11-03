package cn.sdh.backend.service;

import cn.sdh.backend.entity.AgentAction;
import cn.sdh.backend.mapper.AgentActionMapper;
import cn.sdh.backend.mapper.AgentStepMapper;
import cn.sdh.backend.entity.AgentStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentActionCompletionService {
    private final AgentActionMapper actions;
    private final AgentActionJobs jobs;
    private final AgentStepMapper steps;

    @Transactional(rollbackFor = Exception.class)
    public AgentAction complete(AgentAction result) {
        if (actions.finishExecuting(result) != 1) return actions.selectById(result.getId());
        updateStep(result);
        if (result.getExecutionJobId() != null) jobs.enqueueContinuation(result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void publishDecision(AgentAction result) {
        updateStep(result);
        jobs.enqueueContinuation(result);
    }

    private void updateStep(AgentAction result) {
        if (result.getStepId() != null) {
            AgentStep step = new AgentStep(); step.setId(result.getStepId()); step.setActionId(result.getId());
            step.setStatus(result.getStatus()); step.setOutputJson(result.getResultJson() == null ? result.getErrorMessage() : result.getResultJson());
            if (result.getStartedAt() != null && result.getExecutedAt() != null) {
                step.setLatencyMs((int) Math.min(Integer.MAX_VALUE, Math.max(0,
                        java.time.Duration.between(result.getStartedAt(), result.getExecutedAt()).toMillis())));
            }
            steps.updateById(step);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void failBeforeExecution(AgentAction action, String reason) {
        if (actions.failBeforeExecution(action.getId(), AgentActionJobs.version(action), reason) == 1) {
            action.setStatus("FAILED");
            action.setErrorMessage(reason);
            updateStep(action);
            jobs.enqueueContinuation(action);
        }
    }
}
