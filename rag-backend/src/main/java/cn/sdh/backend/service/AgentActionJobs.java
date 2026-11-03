package cn.sdh.backend.service;

import cn.sdh.backend.agent.AgentActionState;
import cn.sdh.backend.entity.AgentAction;
import cn.sdh.backend.entity.PlatformJob;
import cn.sdh.backend.mapper.AgentActionMapper;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The database job table is the transactional handoff; no duplicate outbox is needed. */
@Service
@RequiredArgsConstructor
public class AgentActionJobs {
    private final PlatformJobService jobs;
    private final AgentActionMapper actions;
    public record Payload(Long actionId, int version) {}

    @Transactional(rollbackFor = Exception.class)
    public void enqueueExecution(AgentAction action) {
        int version = version(action);
        PlatformJob job = jobs.enqueue(new PlatformJobService.EnqueueCommand(
                "agent-action:" + action.getId() + ":" + version, "AGENT_ACTION_EXECUTE", "AGENT_ACTION",
                action.getId(), JSON.toJSONString(new Payload(action.getId(), version)), 10, 3, action.getRequestedBy()));
        if (job == null) throw new IllegalStateException("动作任务创建失败，回滚本次提交");
        if (actions.bindExecutionJob(action.getId(), version, job.getId()) != 1) {
            AgentAction current = actions.selectById(action.getId());
            if (current != null && version(current) == version && job.getId().equals(current.getExecutionJobId())) {
                action.setExecutionJobId(job.getId());
                action.setStatus(current.getStatus());
                return;
            }
            throw new IllegalStateException("动作任务绑定失败，回滚本次提交");
        }
        action.setExecutionJobId(job.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void enqueueContinuation(AgentAction action) {
        if (!AgentActionState.isTerminal(action.getStatus())
                || (action.getRunId() == null && action.getWorkflowExecutionId() == null)) return;
        jobs.enqueue(new PlatformJobService.EnqueueCommand(
                "agent-action-result:" + action.getId() + ":" + version(action), "AGENT_ACTION_CONTINUE", "AGENT_ACTION",
                action.getId(), JSON.toJSONString(new Payload(action.getId(), version(action))), 5, 5, action.getRequestedBy()));
    }

    public static int version(AgentAction action) { return action.getExecutionVersion() == null ? 1 : action.getExecutionVersion(); }
}
