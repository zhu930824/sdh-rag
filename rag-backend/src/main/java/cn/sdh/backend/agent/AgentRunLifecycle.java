package cn.sdh.backend.agent;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.AgentAction;
import cn.sdh.backend.entity.AgentRun;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Application publication boundary shared by initial execution, both engines and recovery. No SSE/model calls. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentRunLifecycle {
    private final RunStateService states;
    public record Context(RunExecutionLeaseService.Fence fence, String sources, String routerInfo) {
        public Context { Objects.requireNonNull(fence, "Execution fence is required"); }
    }
    public record Progress(int steps, int inputTokens, int outputTokens, int totalTokens) {
        public Progress {
            if (steps < 0 || inputTokens < 0 || outputTokens < 0 || totalTokens < (long) inputTokens + outputTokens) {
                throw new BusinessException(409, "运行进度计数不合法");
            }
        }
        private void apply(AgentRun run) {
            run.setStepCount(steps); run.setPromptTokens(inputTokens); run.setCompletionTokens(outputTokens); run.setTotalTokens(totalTokens);
        }
    }

    public AgentRun complete(Context context, Progress progress, String answer) {
        AgentRun run = requireRun(context);
        progress.apply(run); run.setStatus(AgentRun.STATUS_COMPLETED); run.setCompletedAt(LocalDateTime.now());
        publish(context, run, answer, "运行状态已变化，不能覆盖最终结果");
        return run;
    }
    public AgentRun pause(Context context, Progress progress, List<AgentAction> pending) {
        if (pending == null || pending.isEmpty() || pending.stream().anyMatch(action -> !AgentActionState.blocksContinuation(action.getStatus()))) {
            throw new BusinessException(409, "暂停运行必须关联尚未完成的业务动作");
        }
        AgentRun run = requireRun(context);
        progress.apply(run);
        run.setStatus(pending.stream().anyMatch(action -> "PENDING_APPROVAL".equals(action.getStatus()))
                ? AgentRun.STATUS_WAITING_APPROVAL : AgentRun.STATUS_WAITING_ACTION);
        run.setPendingActionId(pending.getFirst().getId());
        publish(context, run, "任务已暂停，正在等待审批或业务处理。", "运行状态已变化，不能重复发布暂停状态");
        return run;
    }
    public boolean fail(Context context, Throwable failure) {
        AgentRun run = states.getByRunKey(context.fence().runKey());
        if (run == null || !AgentRun.STATUS_RUNNING.equals(run.getStatus())) return false;
        AgentRunTermination outcome = AgentRunTermination.from(failure);
        run.setStatus(outcome.status()); run.setErrorMessage(cap(outcome.message(), 1000)); run.setCompletedAt(LocalDateTime.now());
        String answer = AgentRun.STATUS_CANCELLED.equals(outcome.status()) ? outcome.message() : "任务未完成：" + cap(outcome.message(), 1000);
        return states.publish(context.fence(), run, AgentRun.STATUS_RUNNING, answer, context.sources(), context.routerInfo(), trace(run));
    }
    public void progress(Context context, Progress progress, List<String> rounds) {
        AgentRun run = new AgentRun(); run.setId(context.fence().runId()); progress.apply(run);
        states.guarded(context.fence(), () -> {
            states.save(run);
            if (rounds != null) states.persistRounds(run.getId(), rounds);
            return true;
        });
    }
    public boolean rejectWaiting(AgentAction action) {
        if (action == null || action.getRunId() == null) return false;
        AgentRun run = states.getById(action.getRunId());
        if (run == null || !AgentRun.isWaiting(run.getStatus())) return false;
        String expected = run.getStatus();
        run.setStatus(AgentRun.STATUS_REJECTED); run.setCompletedAt(LocalDateTime.now());
        return states.publish(run, expected, rejectionAnswer(action), null, null, trace(run));
    }
    public static String rejectionAnswer(AgentAction action) {
        String reason = action.getErrorMessage() == null ? "审批人拒绝" : action.getErrorMessage();
        return "业务动作「" + action.getToolName() + "」已被拒绝(" + reason + "),本次未执行。";
    }
    private AgentRun requireRun(Context context) {
        AgentRun run = states.getByRunKey(context.fence().runKey());
        if (run == null) throw new BusinessException(404, "Agent 运行不存在");
        return run;
    }
    private void publish(Context context, AgentRun run, String answer, String conflict) {
        if (!states.publish(context.fence(), run, AgentRun.STATUS_RUNNING, answer, context.sources(), context.routerInfo(), trace(run))) {
            throw new BusinessException(409, conflict);
        }
    }
    private String trace(AgentRun run) {
        try { return AgentRunTrace.encode(run, states.stepsOf(run.getId())); }
        catch (Exception failure) { log.warn("Agent 轨迹摘要生成失败"); return null; }
    }
    private static String cap(String value, int max) { return value == null ? null : value.substring(0, Math.min(max, value.length())); }
}
