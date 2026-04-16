package cn.sdh.backend.workflow.executor.impl;

import cn.sdh.backend.workflow.executor.NodeExecutor;
import cn.sdh.backend.workflow.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 输出节点执行器
 */
@Slf4j
@Component
public class OutputNodeExecutor implements NodeExecutor {

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) {
        Map<String, Object> output = new HashMap<>();

        // 获取节点配置
        Map<String, Object> nodeData = node.getData();
        if (nodeData == null) {
            output.put("output", input.get("output") != null ? input.get("output") : input.get("query"));
            return output;
        }

        // 获取 outputParams 配置
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outputParams = (List<Map<String, Object>>) nodeData.get("outputParams");
        Map<String, Object> paramValues = new HashMap<>();

        // 处理输出参数
        if (outputParams != null && !outputParams.isEmpty()) {
            for (Map<String, Object> param : outputParams) {
                String paramName = (String) param.get("name");
                if (paramName == null || paramName.isEmpty()) {
                    continue;
                }

                String paramType = (String) param.get("type");
                Object paramValue = null;

                if ("input".equals(paramType)) {
                    // 直接输入的值
                    paramValue = param.get("value");
                } else if ("reference".equals(paramType)) {
                    // 引用其他节点的输出
                    String reference = (String) param.get("referenceNode");
                    log.debug("处理引用参数: {} -> {}", paramName, reference);

                    if (reference != null && reference.contains(".")) {
                        String[] parts = reference.split("\\.");
                        String refNodeId = parts[0];
                        String refParamName = parts[parts.length - 1];

                        // 从当前输入中获取（可能已被合并）
                        if (input.containsKey(refParamName)) {
                            paramValue = input.get(refParamName);
                        }

                        // 如果引用的是 query（输入节点）
                        if (paramValue == null && "query".equals(refParamName)) {
                            paramValue = input.get("query");
                        }

                        // 尝试从节点输出映射中获取
                        if (paramValue == null && input.containsKey("__nodeOutputs__")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Map<String, Object>> nodeOutputs =
                                (Map<String, Map<String, Object>>) input.get("__nodeOutputs__");
                            if (nodeOutputs != null && nodeOutputs.containsKey(refNodeId)) {
                                Map<String, Object> nodeOutput = nodeOutputs.get(refNodeId);
                                paramValue = nodeOutput.get(refParamName);
                            }
                        }

                        log.debug("引用参数 {} 的值: {}", refParamName, paramValue);
                    }
                }

                if (paramValue != null) {
                    paramValues.put(paramName, paramValue);
                }
            }
        }

        // 获取 responseContent 模板
        String responseContent = (String) nodeData.get("responseContent");

        if (responseContent != null && !responseContent.isEmpty()) {
            // 使用模板渲染输出
            log.debug("输出节点配置 - responseContent: {}", responseContent);
            log.debug("参数值映射: {}", paramValues);

            // 替换模板中的 {{参数名}}
            String result = responseContent;
            Pattern pattern = Pattern.compile("\\{\\{(.*?)\\}\\}");
            Matcher matcher = pattern.matcher(responseContent);

            while (matcher.find()) {
                String paramName = matcher.group(1).trim();
                Object paramValue = paramValues.get(paramName);
                String valueStr = paramValue != null ? paramValue.toString() : "";
                result = result.replace("{{" + paramName + "}}", valueStr);
            }

            log.debug("输出节点最终结果: {}", result);
            output.put("output", result);
        } else if (!paramValues.isEmpty()) {
            // 没有模板，但配置了输出参数，只输出配置的参数
            output.putAll(paramValues);
        } else {
            // 没有任何配置，默认输出上游的 output 或 query
            Object defaultOutput = input.get("output");
            if (defaultOutput == null) {
                defaultOutput = input.get("query");
            }
            output.put("output", defaultOutput);
        }

        // 标记这是输出节点的最终输出
        output.put("__isFinalOutput__", true);

        return output;
    }

    @Override
    public String getSupportedNodeType() {
        return "output";
    }
}
