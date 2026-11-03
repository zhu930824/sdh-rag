package cn.sdh.backend.job;

import cn.sdh.backend.entity.AgentAction;
import cn.sdh.backend.entity.PlatformJob;
import cn.sdh.backend.mapper.AgentActionMapper;
import cn.sdh.backend.service.AgentActionExecutor;
import cn.sdh.backend.service.AgentActionJobs;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentActionJobHandler implements PlatformJobHandler {
    private final AgentActionMapper actions;
    private final AgentActionExecutor executor;
    @Override public String jobType() { return "AGENT_ACTION_EXECUTE"; }
    @Override public String execute(PlatformJob job, PlatformJobExecutionContext context) {
        AgentActionJobs.Payload payload = JSON.parseObject(job.getPayloadJson(), AgentActionJobs.Payload.class);
        AgentAction action = actions.selectById(payload.actionId());
        if (action == null || !job.getCreatedBy().equals(action.getRequestedBy())
                || !job.getId().equals(action.getExecutionJobId())
                || AgentActionJobs.version(action) != payload.version()) return "动作不存在或任务版本已过期";
        context.checkpoint();
        AgentAction result = executor.execute(action.getId(), payload.version());
        if ("EXECUTING".equals(result.getStatus())) {
            throw new IllegalStateException("原执行结果尚未确定，禁止重复发送业务请求");
        }
        return "动作状态: " + result.getStatus();
    }
}
