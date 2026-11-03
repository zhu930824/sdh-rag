package cn.sdh.backend.job;

import cn.sdh.backend.agent.AgentApprovalRouter;
import cn.sdh.backend.agent.AgentActionState;
import cn.sdh.backend.entity.AgentAction;
import cn.sdh.backend.entity.AgentRun;
import cn.sdh.backend.entity.PlatformJob;
import cn.sdh.backend.mapper.AgentActionMapper;
import cn.sdh.backend.mapper.AgentRunMapper;
import cn.sdh.backend.service.AgentActionJobs;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentActionContinuationJobHandler implements PlatformJobHandler {
    private final AgentActionMapper actions;
    private final AgentRunMapper runs;
    private final AgentApprovalRouter router;
    @Override public String jobType() { return "AGENT_ACTION_CONTINUE"; }
    @Override public String execute(PlatformJob job, PlatformJobExecutionContext context) {
        AgentActionJobs.Payload payload = JSON.parseObject(job.getPayloadJson(), AgentActionJobs.Payload.class);
        AgentAction action = actions.selectById(payload.actionId());
        if (action == null || !job.getCreatedBy().equals(action.getRequestedBy())
                || AgentActionJobs.version(action) != payload.version()
                || !AgentActionState.isTerminal(action.getStatus())) return "动作已变更，无需处理旧通知";
        if (action.getRunId() != null) {
            AgentRun run = runs.selectById(action.getRunId());
            if (run != null && AgentRun.STATUS_RUNNING.equals(run.getStatus())) {
                // The producer may still be writing its suspension checkpoint.
                throw new IllegalStateException("运行尚未到达挂起边界，稍后重投通知");
            }
        }
        context.checkpoint();
        router.onActionDecided(action, false);
        return "动作结果通知已处理";
    }
}
