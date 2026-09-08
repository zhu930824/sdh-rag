package cn.sdh.backend.agent;

import cn.sdh.backend.entity.AgentAction;
import cn.sdh.backend.entity.AgentRun;
import cn.sdh.backend.workflow.executor.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 审批动作决定后的续跑路由器:
 * - agent_action.runId 非空 → Agent 运行续跑(聊天内联 skip 时由调用方自行 resume)
 * - agent_action.workflowExecutionId 非空 → 工作流暂停续跑(审批通过后自动续跑同一条执行记录)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentApprovalRouter {

    private final AgentAskService agentAskService;
    private final RunStateService runStateService;
    private final WorkflowEngine workflowEngine;

    /**
     * @param skipAutoResume true = 调用方(X-Agent-Resume: skip)自行触发续跑,路由器不动作
     */
    public void onActionDecided(AgentAction action, boolean skipAutoResume) {
        if (action == null || skipAutoResume) {
            return;
        }
        if (action.getRunId() != null) {
            routeAgentRun(action);
            return;
        }
        if (action.getWorkflowExecutionId() != null) {
            routeWorkflowResume(action);
        }
    }

    private void routeAgentRun(AgentAction action) {
        AgentRun run = runStateService.getById(action.getRunId());
        if (run == null) {
            log.warn("Agent 动作 {} 关联的运行已不存在,跳过续跑", action.getId());
            return;
        }
        if ("REJECTED".equals(action.getStatus())) {
            // 确定性收尾,不回调 LLM
            agentAskService.finalizeRejected(action);
            return;
        }
        // SUCCEEDED/FAILED:后台续跑至终态,答案落 chat_history
        agentAskService.resumeInBackground(run.getRunKey(), action.getId());
    }

    /** 工作流动作已决定:后台续跑同一执行记录的下游节点,并把决定结果注入下游引用 */
    private void routeWorkflowResume(AgentAction action) {
        log.info("工作流动作 {} 已决定,触发执行 {} 续跑",
                action.getId(), action.getWorkflowExecutionId());
        if (action.getNodeId() == null) {
            workflowEngine.resumeInBackground(action.getWorkflowExecutionId());
            return;
        }
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        fields.put("actionId", action.getId());
        fields.put("actionStatus", action.getStatus());
        fields.put("approvalRequired", false);
        fields.put("responseCode", action.getResponseCode());
        Object result = parseResult(action.getResultJson());
        fields.put("result", result);
        fields.put("error", action.getErrorMessage());
        Map<String, Map<String, Object>> overrides = Map.of(action.getNodeId(), fields);
        workflowEngine.resumeInBackground(action.getWorkflowExecutionId(), overrides);
    }

    private Object parseResult(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return com.alibaba.fastjson2.JSON.parse(value);
        } catch (Exception e) {
            return value;
        }
    }
}
