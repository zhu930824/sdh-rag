package cn.sdh.backend.service;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.exception.BusinessException;
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
    private final AgentToolHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public record ActionCommand(Long toolId, Map<String, Object> parameters, Object evidence,
                                String idempotencyKey, Long workflowId, String nodeId) {}
    public record DecisionCommand(String reason) {}
    public record FeedbackCommand(String outcome, String comment, Boolean reusableKnowledge) {}

    public AgentAction request(ActionCommand command) {
        return request(command, requireActor());
    }

    public AgentAction request(ActionCommand command, Long actor) {
        if (command == null || command.toolId() == null) throw new BusinessException(400, "请选择业务工具");
        AgentTool tool = toolService.requireUsable(command.toolId(), actor);
        String idempotencyKey = normalizeIdempotencyKey(command.idempotencyKey());
        AgentAction existing = findByIdempotency(tool.getId(), actor, idempotencyKey);
        if (existing != null) return existing;

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
        action.setStatus(requiresApproval(tool) ? "PENDING_APPROVAL" : "READY");
        action.setRequestedAt(LocalDateTime.now());
        action.setUpdateTime(LocalDateTime.now());
        try {
            actions.insert(action);
        } catch (DuplicateKeyException e) {
            AgentAction duplicated = findByIdempotency(tool.getId(), actor, idempotencyKey);
            if (duplicated != null) return duplicated;
            throw e;
        }
        return "READY".equals(action.getStatus()) ? execute(action) : action;
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

    public AgentAction get(Long id) {
        AgentAction action = requireAccessible(id, requireActor());
        return action;
    }

    public AgentAction approve(Long id) {
        Long actor = requireActor();
        AgentAction action = requireAccessible(id, actor);
        if (!actor.equals(action.getApproverId())) throw new BusinessException(403, "仅指定审批人可审批该动作");
        if ("PENDING_APPROVAL".equals(action.getStatus()) && actions.approve(id, actor) != 1) {
            throw new BusinessException(409, "动作状态已变化，请刷新后重试");
        }
        action = actions.selectById(id);
        if ("READY".equals(action.getStatus())) return execute(action);
        return action;
    }

    public AgentAction reject(Long id, DecisionCommand command) {
        Long actor = requireActor();
        AgentAction action = requireAccessible(id, actor);
        if (!actor.equals(action.getApproverId())) throw new BusinessException(403, "仅指定审批人可拒绝该动作");
        String reason = command == null ? null : limitNullable(command.reason(), 1000, "拒绝原因");
        if (actions.reject(id, actor, reason == null ? "审批人拒绝" : reason) != 1) {
            throw new BusinessException(409, "仅待审批动作可以拒绝");
        }
        return actions.selectById(id);
    }

    public AgentAction retry(Long id) {
        Long actor = requireActor();
        AgentAction action = requireAccessible(id, actor);
        if (!actor.equals(action.getRequestedBy())) throw new BusinessException(403, "仅动作发起人可重试");
        if (actions.transition(id, "FAILED", "READY") != 1) throw new BusinessException(409, "仅失败动作可以重试");
        return execute(actions.selectById(id));
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
        return feedback;
    }

    private AgentAction execute(AgentAction action) {
        if (actions.transition(action.getId(), "READY", "EXECUTING") != 1) return actions.selectById(action.getId());
        AgentAction executing = actions.selectById(action.getId());
        try {
            AgentTool tool = toolService.requireUsable(executing.getToolId(), executing.getRequestedBy());
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = objectMapper.readValue(executing.getRequestJson(), Map.class);
            AgentToolHttpClient.ToolResponse response = httpClient.execute(tool, parameters);
            executing.setResponseCode(response.statusCode());
            executing.setExecutedAt(LocalDateTime.now());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                executing.setStatus("SUCCEEDED");
                executing.setResultJson(limitResult(response.body()));
                executing.setErrorMessage(null);
            } else {
                executing.setStatus("FAILED");
                executing.setErrorMessage("工具返回非成功状态: " + response.statusCode());
            }
        } catch (Exception e) {
            log.warn("Agent action {} execution failed: {}", executing.getId(), e.getMessage());
            executing.setStatus("FAILED");
            executing.setExecutedAt(LocalDateTime.now());
            executing.setErrorMessage(limitNullable(e.getMessage(), 1000, "执行错误"));
        }
        executing.setUpdateTime(LocalDateTime.now());
        actions.updateById(executing);
        return executing;
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
        return enumValue(value, List.of("PENDING_APPROVAL", "READY", "EXECUTING", "SUCCEEDED", "FAILED", "REJECTED"), "动作状态");
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

    private String limitResult(String value) {
        if (value == null) return null;
        return value.length() > 2 * 1024 * 1024 ? value.substring(0, 2 * 1024 * 1024) : value;
    }

    private String limitNullable(String value, int max, String label) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new BusinessException(400, label + "不能超过 " + max + " 个字符");
        return normalized;
    }

    private Long requireActor() {
        Long actor = UserContext.getCurrentUserId();
        if (actor == null) throw new BusinessException(401, "请先登录");
        return actor;
    }
}
