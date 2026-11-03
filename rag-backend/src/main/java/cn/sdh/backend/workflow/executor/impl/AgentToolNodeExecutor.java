package cn.sdh.backend.workflow.executor.impl;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.AgentAction;
import cn.sdh.backend.agent.AgentActionState;
import cn.sdh.backend.service.AgentActionService;
import cn.sdh.backend.workflow.executor.NodeExecutor;
import cn.sdh.backend.workflow.model.WorkflowNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AgentToolNodeExecutor implements NodeExecutor {
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{(.*?)}}", Pattern.DOTALL);
    private final AgentActionService actionService;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) throws Exception {
        Map<String, Object> data = node.getData();
        if (data == null || !(data.get("toolId") instanceof Number toolId)) {
            throw new BusinessException(400, "请选择受控业务工具");
        }
        String parametersTemplate = string(data.get("parameters"), "{}");
        String resolved = replaceVariables(parametersTemplate, input);
        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = objectMapper.readValue(resolved, Map.class);
        String evidenceKey = string(data.get("evidenceKey"), "documents");
        Object evidence = input.get(evidenceKey);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("toolId", toolId.longValue());
        output.put("parameters", parameters);
        output.put("evidence", evidence);
        if (Boolean.TRUE.equals(input.get("__debugMode__"))) {
            output.put("preview", true);
            output.put("actionStatus", "PREVIEW");
            return output;
        }

        Long actor = number(input.get("__actorId__"), "执行用户");
        Long workflowId = nullableNumber(input.get("__workflowId__"));
        // 执行记录行 ID:审批通过后用于续跑同一工作流执行
        Long workflowExecutionId = nullableNumber(input.get("__executionId__"));
        String idempotencyKey = replaceVariables(string(data.get("idempotencyKey"), ""), input);
        AgentAction action = actionService.request(new AgentActionService.ActionCommand(
                toolId.longValue(), parameters, evidence,
                idempotencyKey.isBlank() ? null : idempotencyKey, workflowId, node.getId(),
                null, null, workflowExecutionId), actor);
        output.clear();
        output.put("actionId", action.getId());
        output.put("actionStatus", action.getStatus());
        output.put("approvalRequired", "PENDING_APPROVAL".equals(action.getStatus()));
        output.put("responseCode", action.getResponseCode());
        output.put("result", parseResult(action.getResultJson()));
        output.put("error", action.getErrorMessage());
        if (AgentActionState.blocksContinuation(action.getStatus())) output.put("__workflowPaused__", true);
        if ("FAILED".equals(action.getStatus())) {
            throw new BusinessException(502, action.getErrorMessage() == null ? "业务工具执行失败" : action.getErrorMessage());
        }
        if ("REJECTED".equals(action.getStatus())) throw new BusinessException(409, "业务动作已被拒绝");
        return output;
    }

    private Object parseResult(String value) {
        if (value == null || value.isBlank()) return null;
        try { return objectMapper.readValue(value, Object.class); }
        catch (Exception ignored) { return value; }
    }

    private String replaceVariables(String template, Map<String, Object> input) {
        Matcher matcher = VARIABLE.matcher(template == null ? "" : template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            Object value = resolve(input, matcher.group(1).trim());
            String replacement;
            try {
                replacement = value == null ? "" : value instanceof String ? escapeJson((String) value) : objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                throw new BusinessException(400, "无法序列化工具参数变量", e);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Object resolve(Map<String, Object> input, String path) {
        Object value = input;
        for (String part : path.split("\\.")) {
            if (!(value instanceof Map<?, ?> map)) return null;
            value = map.get(part);
        }
        return value;
    }

    private String escapeJson(String value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return json.substring(1, json.length() - 1);
        } catch (Exception e) {
            throw new BusinessException(400, "无法序列化工具参数", e);
        }
    }

    private Long number(Object value, String label) {
        Long result = nullableNumber(value);
        if (result == null) throw new BusinessException(401, label + "缺失");
        return result;
    }

    private Long nullableNumber(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try { return Long.parseLong(String.valueOf(value)); }
        catch (Exception e) { return null; }
    }

    private String string(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    @Override
    public String getSupportedNodeType() { return "tool"; }
}
