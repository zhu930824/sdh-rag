package cn.sdh.backend.service;

import cn.sdh.backend.agent.ToolDefinitionFingerprint;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.demo.InternalToolDispatcher;
import cn.sdh.backend.entity.AgentAction;
import cn.sdh.backend.entity.AgentTool;
import cn.sdh.backend.mapper.AgentActionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Map;

/** HTTP is deliberately outside the transactions that accept and complete a write. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentActionExecutor {
    private final AgentActionMapper actions;
    private final AgentToolService tools;
    private final AgentToolHttpClient http;
    private final InternalToolDispatcher internal;
    private final ObjectMapper json;
    private final AgentActionCompletionService completion;

    public AgentAction execute(Long id, int version) {
        AgentAction action = actions.selectById(id);
        if (action == null) throw new BusinessException(404, "Agent 动作不存在");
        if (AgentActionJobs.version(action) != version || !"READY".equals(action.getStatus())) return action;
        if (actions.claimReady(id, version) != 1) return actions.selectById(id);
        action = actions.selectById(id);
        boolean dispatchedWrite = false;
        try {
            AgentTool tool = tools.requireUsable(action.getToolId(), action.getRequestedBy());
            if (action.getToolDefinitionHash() != null
                    && !action.getToolDefinitionHash().equals(ToolDefinitionFingerprint.of(tool))) {
                throw new BusinessException(409, "工具定义或审批策略已变化，请重新提交动作审批");
            }
            Map<String, Object> parameters = json.readValue(action.getRequestJson(), new TypeReference<>() {});
            // Internal writes are also uncertain if they throw after committing a side effect.
            dispatchedWrite = !"GET".equalsIgnoreCase(tool.getHttpMethod());
            AgentToolHttpClient.ToolResponse response = InternalToolDispatcher.isInternal(tool.getEndpoint())
                    ? internal.execute(tool, parameters, action.getRequestedBy()) : http.execute(tool, parameters);
            action.setResponseCode(response.statusCode());
            boolean succeeded = response.statusCode() >= 200 && response.statusCode() < 300;
            action.setStatus(succeeded ? "SUCCEEDED" : dispatchedWrite ? "UNKNOWN" : "FAILED");
            action.setResultJson(succeeded ? response.body() : null);
            action.setErrorMessage(succeeded ? null : "工具返回非成功状态: " + response.statusCode());
        } catch (Exception failure) {
            action.setStatus(dispatchedWrite ? "UNKNOWN" : "FAILED");
            String message = failure.getMessage() == null ? "工具执行失败" : failure.getMessage();
            action.setErrorMessage(message.substring(0, Math.min(1000, message.length())));
            log.warn("Agent 动作执行未完成 actionId={}, status={}", id, action.getStatus());
        }
        action.setExecutedAt(LocalDateTime.now());
        action.setUpdateTime(LocalDateTime.now());
        return completion.complete(action);
    }
}
