package cn.sdh.backend.service;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.agent.ToolDefinitionFingerprint;
import cn.sdh.backend.agent.RunExecutionLeaseService;
import cn.sdh.backend.entity.AgentAction;
import cn.sdh.backend.entity.AgentActionFeedback;
import cn.sdh.backend.entity.AgentTool;
import cn.sdh.backend.mapper.AgentActionFeedbackMapper;
import cn.sdh.backend.mapper.AgentActionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentActionService {
    private static final int MAX_JSON_CHARS = 256 * 1024;
    private final AgentActionMapper actions;
    private final AgentActionFeedbackMapper feedbacks;
    private final AgentToolService toolService;
    private final AgentActionExecutor executor;
    private final AgentActionJobs actionJobs;
    private final AgentActionCompletionService completion;
    private final ObjectMapper objectMapper;
    private final AgentImprovementService improvementService;
    private final RunExecutionLeaseService leases;

    public record ActionCommand(Long toolId, Map<String, Object> parameters, Object evidence,
                                String idempotencyKey, Long workflowId, String nodeId,
                                Long runId, Long stepId, Long workflowExecutionId, String toolCallId,
                                RunExecutionLeaseService.Fence fence) {
        public ActionCommand(Long toolId, Map<String, Object> parameters, Object evidence,
                             String idempotencyKey, Long workflowId, String nodeId,
                             Long runId, Long stepId, Long workflowExecutionId, String toolCallId) {
            this(toolId, parameters, evidence, idempotencyKey, workflowId, nodeId,
                    runId, stepId, workflowExecutionId, toolCallId, null);
        }
        public ActionCommand(Long toolId, Map<String, Object> parameters, Object evidence,
                             String idempotencyKey, Long workflowId, String nodeId,
                             Long runId, Long stepId, Long workflowExecutionId) {
            this(toolId, parameters, evidence, idempotencyKey, workflowId, nodeId,
                    runId, stepId, workflowExecutionId, null);
        }
    }
    public record DecisionCommand(String reason) {}
    public record FeedbackCommand(String outcome, String comment, Boolean reusableKnowledge) {}
    public record ReconcileCommand(String outcome, String receipt) {}

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public AgentAction request(ActionCommand command) {
        return request(command, requireActor());
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public AgentAction request(ActionCommand command, Long actor) {
        if (command == null || command.toolId() == null) throw new BusinessException(400, "请选择业务工具");
        AgentTool tool = toolService.requireUsable(command.toolId(), actor);
        AgentAction registered;
        if (command.fence() != null) {
            if (!java.util.Objects.equals(command.runId(), command.fence().runId())
                    || !java.util.Objects.equals(actor, command.fence().userId())) {
                throw new BusinessException(409, "动作与执行租约不匹配");
            }
            registered = leases.guarded(command.fence(), () -> register(command, actor, tool));
        } else {
            if (command.runId() != null) throw new BusinessException(409, "运行内动作必须携带执行租约");
            registered = leases.transaction(() -> register(command, actor, tool));
        }
        // The registration transaction has committed before any external call starts.
        if ("READY".equals(registered.getStatus()) && "GET".equalsIgnoreCase(tool.getHttpMethod())) {
            return executor.execute(registered.getId(), registered.getExecutionVersion());
        }
        return registered;
    }

    private AgentAction register(ActionCommand command, Long actor, AgentTool tool) {
        String idempotencyKey = normalizeIdempotencyKey(command.idempotencyKey());
        // Older SDK runs used a different key format; keep their existing operation identity on resume.
        if (command.runId() != null && command.toolCallId() != null) {
            for (AgentAction previous : listByRun(command.runId())) {
                if (tool.getId().equals(previous.getToolId()) && sameParameters(previous, command.parameters())) {
                    idempotencyKey = previous.getIdempotencyKey();
                    break;
                }
            }
        }
        AgentAction existing = findByIdempotency(tool.getId(), actor, idempotencyKey);
        if (existing != null) return verifySameRequest(existing, command.parameters());

        AgentAction action = new AgentAction();
        action.setWorkflowId(command.workflowId());
        action.setNodeId(limitNullable(command.nodeId(), 100, "节点 ID"));
        action.setToolId(tool.getId());
        action.setToolName(tool.getName());
        action.setRequestedBy(actor);
        action.setApproverId(tool.getApproverId());
        action.setIdempotencyKey(idempotencyKey);
        action.setRequestJson(toJson(command.parameters() == null ? Map.of() : command.parameters(), "请求参数"));
        action.setEvidenceJson(command.evidence() == null ? null : toJson(command.evidence(), "决策依据"));
        action.setRunId(command.runId());
        action.setToolCallId(command.toolCallId());
        action.setToolDefinitionHash(ToolDefinitionFingerprint.of(tool));
        action.setExecutionVersion(1);
        action.setStepId(command.stepId());
        action.setWorkflowExecutionId(command.workflowExecutionId());
        action.setStatus(requiresApproval(tool) ? "PENDING_APPROVAL" : "READY");
        action.setRequestedAt(LocalDateTime.now());
        action.setUpdateTime(LocalDateTime.now());
        try {
            actions.insert(action);
        } catch (DuplicateKeyException e) {
            AgentAction duplicated = actions.findIdempotentCurrent(tool.getId(), actor, idempotencyKey);
            if (duplicated != null) return verifySameRequest(duplicated, command.parameters());
            throw e;
        }
        if ("READY".equals(action.getStatus()) && !"GET".equalsIgnoreCase(tool.getHttpMethod())) {
            actionJobs.enqueueExecution(action);
        }
        return action;
    }

    public List<AgentAction> listMine(String status) {
        Long actor = requireActor();
        LambdaQueryWrapper<AgentAction> query = new LambdaQueryWrapper<AgentAction>()
                .and(q -> q.eq(AgentAction::getRequestedBy, actor).or().eq(AgentAction::getApproverId, actor));
        if (status != null && !status.isBlank()) query.eq(AgentAction::getStatus, normalizeStatus(status));
        return actions.selectList(query.orderByDesc(AgentAction::getRequestedAt).last("LIMIT 200"));
    }

    public List<AgentAction> listPending() {
        Long actor = requireActor();
        return actions.selectList(new LambdaQueryWrapper<AgentAction>()
                .eq(AgentAction::getApproverId, actor)
                .eq(AgentAction::getStatus, "PENDING_APPROVAL")
                .orderByAsc(AgentAction::getRequestedAt).last("LIMIT 200"));
    }

    /** 幂等查询(供 Agent 决策循环复用:同幂等键已有动作直接复用其结果/状态) */
    public AgentAction findIdempotent(Long toolId, Long actor, String idempotencyKey) {
        return findByIdempotency(toolId, actor, idempotencyKey);
    }

    /** 按 Agent run 查动作(审批续跑注入/审计用) */
    public List<AgentAction> listByRun(Long runId) {
        if (runId == null) return List.of();
        return actions.selectList(new LambdaQueryWrapper<AgentAction>()
                .eq(AgentAction::getRunId, runId)
                .orderByAsc(AgentAction::getRequestedAt));
    }

    /** 按工作流执行记录查动作，供受保护的手工续跑校验与结果注入。 */
    public List<AgentAction> listByWorkflowExecution(Long executionId) {
        if (executionId == null) return List.of();
        return actions.selectList(new LambdaQueryWrapper<AgentAction>()
                .eq(AgentAction::getWorkflowExecutionId, executionId)
                .orderByAsc(AgentAction::getRequestedAt));
    }

    public AgentAction get(Long id) {
        AgentAction action = requireAccessible(id, requireActor());
        return action;
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentAction approve(Long id) {
        Long actor = requireActor();
        AgentAction action = requireAccessible(id, actor);
        if (!actor.equals(action.getApproverId())) throw new BusinessException(403, "仅指定审批人可审批该动作");
        if ("PENDING_APPROVAL".equals(action.getStatus()) && actions.approve(id, actor) != 1) {
            throw new BusinessException(409, "动作状态已变化，请刷新后重试");
        }
        action = actions.selectById(id);
        if ("READY".equals(action.getStatus())) actionJobs.enqueueExecution(action);
        return action;
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentAction reject(Long id, DecisionCommand command) {
        Long actor = requireActor();
        AgentAction action = requireAccessible(id, actor);
        if (!actor.equals(action.getApproverId())) throw new BusinessException(403, "仅指定审批人可拒绝该动作");
        String reason = command == null ? null : limitNullable(command.reason(), 1000, "拒绝原因");
        if (actions.reject(id, actor, reason == null ? "审批人拒绝" : reason) != 1) {
            throw new BusinessException(409, "仅待审批动作可以拒绝");
        }
        AgentAction rejected = actions.selectById(id);
        completion.publishDecision(rejected);
        return rejected;
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentAction retry(Long id) {
        Long actor = requireActor();
        AgentAction action = requireAccessible(id, actor);
        if (!actor.equals(action.getRequestedBy())) throw new BusinessException(403, "仅动作发起人可重试");
        if (actions.prepareRetry(id) != 1) throw new BusinessException(409, "仅失败动作可以重试");
        AgentAction ready = actions.selectById(id);
        actionJobs.enqueueExecution(ready);
        return ready;
    }

    /** Explicit human reconciliation; UNKNOWN can never use the retry endpoint directly. */
    @Transactional(rollbackFor = Exception.class)
    public AgentAction reconcile(Long id, ReconcileCommand command) {
        AgentAction action = requireAccessible(id, requireActor());
        if (command == null) throw new BusinessException(400, "请填写业务核对结果");
        String outcome = enumValue(command.outcome(), List.of("SUCCEEDED", "FAILED"), "核对结果");
        String receipt = limitNullable(command.receipt(), 1000, "核对凭据");
        if (receipt == null) throw new BusinessException(400, "请填写下游回执或确认未执行的依据");
        if (actions.reconcile(id, outcome, receipt, requireActor()) != 1) {
            throw new BusinessException(409, "仅待核对动作可以提交核对结果");
        }
        AgentAction reconciled = actions.selectById(id);
        completion.publishDecision(reconciled);
        return reconciled;
    }

    public AgentActionFeedback feedback(Long id, FeedbackCommand command) {
        Long actor = requireActor();
        AgentAction action = requireAccessible(id, actor);
        if (!actor.equals(action.getRequestedBy())) throw new BusinessException(403, "仅动作发起人可反馈业务结果");
        if (!"SUCCEEDED".equals(action.getStatus())) throw new BusinessException(400, "仅执行成功的动作可反馈业务结果");
        if (command == null) throw new BusinessException(400, "反馈内容不能为空");
        String outcome = enumValue(command.outcome(), List.of("SUCCEEDED", "PARTIAL", "FAILED"), "业务结果");
        AgentActionFeedback feedback = feedbacks.selectOne(new LambdaQueryWrapper<AgentActionFeedback>()
                .eq(AgentActionFeedback::getActionId, id).eq(AgentActionFeedback::getUserId, actor));
        if (feedback == null) {
            feedback = new AgentActionFeedback();
            feedback.setActionId(id);
            feedback.setUserId(actor);
            feedback.setCreateTime(LocalDateTime.now());
        }
        feedback.setOutcome(outcome);
        feedback.setComment(limitNullable(command.comment(), 1000, "反馈说明"));
        feedback.setReusableKnowledge(Boolean.TRUE.equals(command.reusableKnowledge()) ? 1 : 0);
        if (feedback.getId() == null) feedbacks.insert(feedback); else feedbacks.updateById(feedback);
        improvementService.captureFeedback(action, feedback);
        return feedback;
    }

    private AgentAction verifySameRequest(AgentAction existing, Map<String, Object> parameters) {
        if (!sameParameters(existing, parameters)) throw new BusinessException(409, "同一幂等键不能用于不同请求参数");
        return existing;
    }

    private boolean sameParameters(AgentAction existing, Map<String, Object> parameters) {
        try {
            return objectMapper.readTree(existing.getRequestJson()).equals(
                    objectMapper.valueToTree(parameters == null ? Map.of() : parameters));
        } catch (Exception error) {
            throw new BusinessException(409, "已有动作参数无法校验，禁止重放", error);
        }
    }

    private AgentAction requireAccessible(Long id, Long actor) {
        AgentAction action = actions.selectById(id);
        if (action == null) throw new BusinessException(404, "Agent 动作不存在");
        if (!actor.equals(action.getRequestedBy()) && !actor.equals(action.getApproverId())) {
            throw new BusinessException(403, "无权访问该 Agent 动作");
        }
        return action;
    }

    private AgentAction findByIdempotency(Long toolId, Long actor, String key) {
        return actions.selectOne(new LambdaQueryWrapper<AgentAction>()
                .eq(AgentAction::getToolId, toolId)
                .eq(AgentAction::getRequestedBy, actor)
                .eq(AgentAction::getIdempotencyKey, key));
    }

    private boolean requiresApproval(AgentTool tool) {
        return "ALWAYS".equals(tool.getApprovalMode()) ||
                ("HIGH_RISK".equals(tool.getApprovalMode()) && !"LOW".equals(tool.getRiskLevel()));
    }

    private String normalizeIdempotencyKey(String value) {
        String key = value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
        if (key.length() > 128 || !key.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new BusinessException(400, "幂等键格式不正确");
        }
        return key;
    }

    private String normalizeStatus(String value) {
        return enumValue(value, List.of("PENDING_APPROVAL", "READY", "EXECUTING", "UNKNOWN", "SUCCEEDED", "FAILED", "REJECTED"), "动作状态");
    }

    private String enumValue(String value, List<String> allowed, String label) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new BusinessException(400, label + "不正确");
        return normalized;
    }

    private String toJson(Object value, String label) {
        try {
            String json = objectMapper.writeValueAsString(value);
            if (json.length() > MAX_JSON_CHARS) throw new BusinessException(400, label + "超过 256KB 限制");
            return json;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, label + "不是有效 JSON 数据", e);
        }
    }

    private String limitNullable(String value, int max, String label) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new BusinessException(400, label + "不能超过 " + max + " 个字符");
        return normalized;
    }

    private String truncateNullable(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.length() > max ? normalized.substring(0, max) : normalized;
    }

    private Long requireActor() {
        Long actor = UserContext.getCurrentUserId();
        if (actor == null) throw new BusinessException(401, "请先登录");
        return actor;
    }
}
