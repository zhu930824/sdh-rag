package cn.sdh.backend.workflow.executor.impl;

import cn.sdh.backend.service.factory.ChatModelFactory;
import cn.sdh.backend.workflow.dto.ExecutionEvent;
import cn.sdh.backend.workflow.executor.NodeExecutor;
import cn.sdh.backend.workflow.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 节点执行器
 */
@Slf4j
@Component
public class LlmNodeExecutor implements NodeExecutor {

    @Autowired
    private ChatModelFactory chatModelFactory;

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) throws Exception {
        return execute(node, input, null);
    }

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input,
                                        Consumer<ExecutionEvent> progressCallback) throws Exception {
        Map<String, Object> output = new HashMap<>();
        Map<String, Object> data = node.getData();

        if (data == null) {
            output.put("output", "LLM 节点未配置");
            return output;
        }

        // 获取配置
        String model = (String) data.get("model");
        String systemPrompt = (String) data.get("systemPrompt");
        String promptTemplate = (String) data.get("prompt");
        Double temperature = data.get("temperature") != null
            ? ((Number) data.get("temperature")).doubleValue() : 0.7;
        Integer maxTokens = data.get("maxTokens") != null
            ? ((Number) data.get("maxTokens")).intValue() : 2000;

        log.info("LLM 节点配置 - model: {}, temperature: {}, maxTokens: {}", model, temperature, maxTokens);

        // 处理提示词模板
        String userPrompt = processPromptTemplate(promptTemplate, data, input);
        log.debug("最终提示词: {}", userPrompt);

        if (userPrompt == null || userPrompt.isEmpty()) {
            output.put("output", "提示词为空");
            return output;
        }

        // 调用 LLM
        String response;
        try {
            // 从工厂获取ChatModel
            ChatModel chatModel = chatModelFactory.getModel(model);

            if (chatModel == null) {
                log.warn("ChatModel 未配置，返回模拟响应");
                response = "【模拟响应】ChatModel 未配置，请检查模型配置。\n\n您的提示词是：" + userPrompt;
            } else {
                ChatClient.Builder builder = ChatClient.builder(chatModel);

                if (systemPrompt != null && !systemPrompt.isEmpty()) {
                    builder.defaultSystem(systemPrompt);
                }

                ChatClient client = builder.build();

                response = client.prompt()
                    .user(userPrompt)
                    .call()
                    .content();

                log.debug("LLM 响应: {}", response);
            }
        } catch (Exception e) {
            log.error("LLM 调用失败", e);
            response = "LLM 调用失败: " + e.getMessage();
        }

        output.put("output", response);
        return output;
    }

    /**
     * 处理提示词模板，替换变量
     */
    @SuppressWarnings("unchecked")
    private String processPromptTemplate(String template, Map<String, Object> nodeData, Map<String, Object> input) {
        if (template == null || template.isEmpty()) {
            return "";
        }

        // 获取输入参数配置
        List<Map<String, Object>> inputParams = (List<Map<String, Object>>) nodeData.get("inputParams");
        Map<String, String> paramValues = new HashMap<>();

        if (inputParams != null) {
            for (Map<String, Object> param : inputParams) {
                String name = (String) param.get("name");
                String type = (String) param.get("type");

                if ("input".equals(type)) {
                    // 直接输入的值
                    paramValues.put(name, (String) param.get("value"));
                } else if ("reference".equals(type)) {
                    // 引用其他节点的输出
                    String reference = (String) param.get("referenceNode");
                    if (reference != null && reference.contains(".")) {
                        String[] parts = reference.split("\\.");
                        String refParamName = parts[parts.length - 1];

                        Object refValue = input.get(refParamName);
                        if (refValue != null) {
                            paramValues.put(name, refValue.toString());
                        }
                    }
                }
            }
        }

        // 替换模板中的 {{参数名}}
        String result = template;
        Pattern pattern = Pattern.compile("\\{\\{(.*?)\\}\\}");
        Matcher matcher = pattern.matcher(template);

        while (matcher.find()) {
            String paramName = matcher.group(1).trim();
            String paramValue = paramValues.getOrDefault(paramName, "");
            result = result.replace("{{" + paramName + "}}", paramValue);
        }

        return result;
    }

    @Override
    public String getSupportedNodeType() {
        return "llm";
    }
}
