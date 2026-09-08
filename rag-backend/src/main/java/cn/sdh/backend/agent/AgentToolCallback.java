package cn.sdh.backend.agent;

import cn.sdh.backend.entity.AgentTool;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;

/**
 * 把 agent_tool 行动态包装成 Spring AI ToolCallback(决策循环按用户可见集实例化)。
 * 工具参数由 LLM 依 input_schema 生成;实际执行/审批门控由 Executor 承接。
 */
@Getter
@RequiredArgsConstructor
public class AgentToolCallback implements ToolCallback {

    /** 工具执行器:入参为工具行 + 参数 Map + 原始参数 JSON,返回给模型的文本 */
    @FunctionalInterface
    public interface Executor {
        String execute(AgentTool tool, Map<String, Object> args, String argsJson) throws Exception;
    }

    private static final String LOOSE_SCHEMA = "{\"type\":\"object\"}";

    private final AgentTool tool;
    private final Executor executor;

    @Override
    public ToolDefinition getToolDefinition() {
        String schema = tool.getInputSchema();
        if (schema == null || schema.isBlank()) {
            schema = LOOSE_SCHEMA;
        }
        String description = tool.getDescription();
        if (description == null || description.isBlank()) {
            description = tool.getName();
        }
        return ToolDefinition.builder()
                .name(tool.getCode())
                .description(description)
                .inputSchema(schema)
                .build();
    }

    @Override
    public String call(String toolInput) {
        Map<String, Object> args;
        try {
            JSONObject parsed = JSON.parseObject(toolInput);
            args = parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            // 模型输出非法 JSON 时给宽松空参,由工具侧参数校验兜底
            args = Map.of("input", toolInput == null ? "" : toolInput);
        }
        try {
            return executor.execute(tool, args, toolInput == null ? "{}" : toolInput);
        } catch (Exception e) {
            // 门控/执行异常不得中断模型内部循环,以结构化错误返回
            return "{\"__agent_error__\":true,\"message\":\""
                    + String.valueOf(e.getMessage()).replace("\"", "'").substring(0,
                            Math.min(300, String.valueOf(e.getMessage()).length()))
                    + "\"}";
        }
    }
}
