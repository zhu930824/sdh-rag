package cn.sdh.backend.agent;

import cn.sdh.backend.entity.AgentAction;
import cn.sdh.backend.entity.AgentRun;
import cn.sdh.backend.service.AgentActionService;
import cn.sdh.backend.service.PlatformJobService;
import cn.sdh.backend.service.WorkflowExecutionService;
import cn.sdh.backend.entity.WorkflowExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.List;

/**
 * 审批动作决定后的续跑路由器:
 * - agent_action.runId 非空 → Agent 运行可靠续跑
 * - agent_action.workflowExecutionId 非空 → 工作流暂停续跑(审批通过后自动续跑同一条执行记录)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentApprovalRouter {

    private final AgentAskService agentAskService;
    private final RunStateService runStateService;
    private final AgentActionService agentActionService;
    private final PlatformJobService jobs;
    private final WorkflowExecutionService workflowExecutions;

    /**
     * @param skipAutoResume legacy compatibility flag; durable result jobs always pass false
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
        List<AgentAction> runActions = agentActionService.listByRun(run.getId());
        AgentAction pending = runActions.stream()
                .filter(item -> AgentActionState.blocksContinuation(item.getStatus()))
                .findFirst().orElse(null);
        if (pending != null) {
            runStateService.updatePendingAction(run.getId(), pending.getId());
            return;
        }
        AgentAction rejected = runActions.stream()
                .filter(item -> "REJECTED".equals(item.getStatus()))
                .findFirst().orElse(null);
        if (rejected != null) {
            // 多动作场景中任一拒绝均确定性收尾，不让其余动作的批准掩盖拒绝。
            agentAskService.finalizeRejected(rejected);
            return;
        }
        // SUCCEEDED/FAILED:后台续跑至终态,答案落 chat_history
        agentAskService.resumeInBackground(run.getRunKey(), action.getId());
    }

    /** 工作流动作已决定:后台续跑同一执行记录的下游节点,并把决定结果注入下游引用 */
    private void routeWorkflowResume(AgentAction action) {
        WorkflowExecution execution = workflowExecutions.getById(action.getWorkflowExecutionId());
        if (execution == null) return;
        jobs.enqueue(new PlatformJobService.EnqueueCommand(
                "workflow-action-resume:" + action.getId() + ":" + cn.sdh.backend.service.AgentActionJobs.version(action),
                "WORKFLOW_ACTION_RESUME", "WORKFLOW_EXECUTION", execution.getId(),
                com.alibaba.fastjson2.JSON.toJSONString(Map.of("executionId", execution.getId())),
                5, 5, execution.getUserId()));
    }
}
