package cn.sdh.backend.job;

import cn.sdh.backend.agent.AgentAskService;
import cn.sdh.backend.agent.AgentRunRecoveryService;
import cn.sdh.backend.agent.RunStateService;
import cn.sdh.backend.entity.AgentRun;
import cn.sdh.backend.entity.PlatformJob;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AgentRecoveryJobHandler implements PlatformJobHandler {
    private final AgentRunRecoveryService recovery;
    private final AgentAskService agents;
    private final RunStateService runs;
    @Override public String jobType() { return "AGENT_RECOVER"; }

    @Override public String execute(PlatformJob job, PlatformJobExecutionContext context) {
        var payload = JSON.parseObject(job.getPayloadJson(), AgentRunRecoveryService.Payload.class);
        if (payload == null || payload.runKey() == null || payload.generation() <= 0) throw new IllegalArgumentException("恢复任务参数无效");
        context.checkpoint();
        if (!recovery.begin(payload, job)) return "旧恢复批次或运行已启动，跳过重复执行";
        // Stream deltas can be frequent. Check job ownership at most once per second, also during blocking calls via the run heartbeat.
        Runnable checkpoint = new Runnable() {
            private long lastCheck;
            @Override public synchronized void run() {
                long now = System.nanoTime();
                if (lastCheck == 0 || now - lastCheck >= Duration.ofSeconds(1).toNanos()) {
                    context.checkpoint(); lastCheck = now;
                }
            }
        };
        try {
            agents.restartRecovered(payload.runKey(), job.getCreatedBy(), checkpoint).blockLast(Duration.ofMinutes(10));
            AgentRun result = runs.getByRunKey(payload.runKey());
            if (result != null && AgentRun.STATUS_CANCELLED.equals(result.getStatus())) throw new PlatformJobCancelledException();
            if (result == null || !java.util.List.of(AgentRun.STATUS_COMPLETED, AgentRun.STATUS_WAITING_ACTION,
                    AgentRun.STATUS_WAITING_APPROVAL).contains(result.getStatus())) {
                throw new IllegalStateException("恢复运行未完成，请查看运行状态与轨迹");
            }
            return "恢复后状态: " + result.getStatus();
        } finally {
            recovery.failBeforeClaim(payload, job.getId());
        }
    }
}
