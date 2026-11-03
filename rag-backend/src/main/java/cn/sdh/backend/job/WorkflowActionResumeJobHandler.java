package cn.sdh.backend.job;

import cn.sdh.backend.agent.AgentActionState;
import cn.sdh.backend.entity.*;
import cn.sdh.backend.service.*;
import cn.sdh.backend.workflow.executor.WorkflowEngine;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
public class WorkflowActionResumeJobHandler implements PlatformJobHandler {
    private final WorkflowExecutionService executions;
    private final AgentActionService actions;
    private final WorkflowEngine engine;
    public record Payload(Long executionId) {}
    @Override public String jobType() { return "WORKFLOW_ACTION_RESUME"; }
    @Override public String execute(PlatformJob job, PlatformJobExecutionContext context) {
        Payload payload = JSON.parseObject(job.getPayloadJson(), Payload.class);
        WorkflowExecution execution = executions.getById(payload.executionId());
        if (execution == null || !job.getCreatedBy().equals(execution.getUserId())) {
            throw new IllegalStateException("工作流任务与执行归属不一致");
        }
        if (List.of("SUCCESS", "FAILED", "CANCELLED").contains(execution.getStatus().toUpperCase(Locale.ROOT))) {
            return "工作流已结束，无需重复执行";
        }
        if (!"WAITING_APPROVAL".equals(execution.getStatus())) {
            throw new IllegalStateException("工作流尚未写入暂停游标，稍后重试");
        }
        List<AgentAction> related = actions.listByWorkflowExecution(execution.getId());
        if (related.isEmpty() || related.stream().anyMatch(action -> AgentActionState.blocksContinuation(action.getStatus()))) {
            return "仍有业务动作等待处理，本次通知无需续跑";
        }
        context.checkpoint();
        if (related.stream().anyMatch(action -> !"SUCCEEDED".equals(action.getStatus()))) {
            if (executions.transition(execution.getId(), "WAITING_APPROVAL", "FAILED")) {
                execution.setStatus("FAILED");
                execution.setErrorMessage("业务动作失败或被拒绝，后续节点未执行");
                execution.setEndTime(LocalDateTime.now());
                executions.update(execution);
            }
            return "业务动作未成功，已终止下游执行";
        }
        Map<String, Map<String, Object>> overrides = new LinkedHashMap<>();
        for (AgentAction action : related) {
            if (action.getNodeId() == null) continue;
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("actionId", action.getId()); fields.put("actionStatus", action.getStatus());
            fields.put("approvalRequired", false); fields.put("responseCode", action.getResponseCode());
            fields.put("result", parseResult(action.getResultJson())); fields.put("error", action.getErrorMessage());
            overrides.put(action.getNodeId(), fields);
        }
        var result = engine.resume(execution.getId(), execution.getUserId(), event -> context.checkpoint(), overrides);
        return "工作流状态: " + result.getStatus();
    }
    private Object parseResult(String json) {
        if (json == null || json.isBlank()) return null;
        try { return JSON.parse(json); } catch (Exception ignored) { return json; }
    }
}
