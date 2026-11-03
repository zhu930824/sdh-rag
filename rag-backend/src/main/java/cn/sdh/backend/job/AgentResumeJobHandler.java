package cn.sdh.backend.job;

import cn.sdh.backend.agent.AgentAskService;
import cn.sdh.backend.agent.RunStateService;
import cn.sdh.backend.entity.AgentRun;
import cn.sdh.backend.entity.PlatformJob;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AgentResumeJobHandler implements PlatformJobHandler {
    private final AgentAskService agents;
    private final RunStateService runs;
    public record Payload(String runKey, Long actionId) {}
    @Override public String jobType() { return "AGENT_RESUME"; }
    @Override public String execute(PlatformJob job, PlatformJobExecutionContext context) {
        Payload payload = JSON.parseObject(job.getPayloadJson(), Payload.class);
        AgentRun run = runs.getByRunKey(payload.runKey());
        if (run == null || !job.getCreatedBy().equals(run.getUserId())) {
            throw new IllegalStateException("续跑任务与运行归属不一致");
        }
        if (java.util.List.of(AgentRun.STATUS_COMPLETED, AgentRun.STATUS_REJECTED, AgentRun.STATUS_CANCELLED)
                .contains(run.getStatus())) return "运行已结束，无需重复恢复";
        context.checkpoint();
        agents.resumeFlux(payload.runKey(), run.getUserId(), payload.actionId())
                .doOnNext(event -> context.checkpoint()).blockLast(Duration.ofMinutes(10));
        AgentRun completed = runs.getByRunKey(payload.runKey());
        if (AgentRun.STATUS_FAILED.equals(completed.getStatus())) throw new IllegalStateException("Agent 续跑失败，请查看运行轨迹");
        return "Agent 状态: " + completed.getStatus();
    }
}
