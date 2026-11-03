package cn.sdh.backend.agent;

import cn.sdh.backend.entity.AgentRun;
import cn.sdh.backend.job.PlatformJobCancelledException;
import java.util.Collections;
import java.util.IdentityHashMap;

/** Cancellation remains a distinct terminal fact even when a model/SDK wraps the exception. */
public record AgentRunTermination(String status, String message) {
    public static AgentRunTermination from(Throwable failure) {
        var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (Throwable current = failure; current != null && visited.add(current); current = current.getCause()) {
            if (current instanceof PlatformJobCancelledException) return new AgentRunTermination(AgentRun.STATUS_CANCELLED, "任务已取消");
        }
        return new AgentRunTermination(AgentRun.STATUS_FAILED,
                failure == null || failure.getMessage() == null ? "运行执行失败" : failure.getMessage());
    }
}
