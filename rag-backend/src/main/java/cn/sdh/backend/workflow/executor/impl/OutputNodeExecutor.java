package cn.sdh.backend.workflow.executor.impl;

import cn.sdh.backend.workflow.executor.NodeExecutor;
import cn.sdh.backend.workflow.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

        // 获取 responseContent 模板
        String responseContent = (String) nodeData.get("responseContent");
        if (responseContent == null || responseContent.isEmpty()) {
            output.put("output", input.get("output") != null ? input.get("output") : input.get("query"));
            return output;
        }

        log.debug("输出节点配置 - responseContent: {}", responseContent);
        log.debug("输出节点输入数据: {}", input);

        // 获取 outputParams 配置
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outputParams = (List<Map<String, Object>>) nodeData.get("outputParams");
        Map<String, String> paramValues = new HashMap<>();

        if (outputParams != null) {
            for (Map<String, Object> param : outputParams) {
                String paramName = (String) param.get("name");
                String paramType = (String) param.get("type");

                if ("input".equals(paramType)) {
                    // 直接输入的值
                    paramValues.put(paramName, (String) param.get("value"));
                } else if ("reference".equals(paramType)) {
                    // 引用其他节点的输出
                    String reference = (String) param.get("referenceNode");
                    log.debug("处理引用参数: {} -> {}", paramName, reference);

                    if (reference != null && reference.contains(".")) {
                        String[] parts = reference.split("\\.");
                        String refNodeId = parts[0];
                        String refParamName = parts[parts.length - 1];

                        Object refValue = null;

                        // 从输入中获取引用值
                        if (input.containsKey(refParamName)) {
                            refValue = input.get(refParamName);
                        }

                        // 如果引用的是 query（输入节点）
                        if (refValue == null && "query".equals(refParamName)) {
                            refValue = input.get("query");
                        }

                        // 尝试从节点输出映射中获取
                        if (refValue == null && input.containsKey("__nodeOutputs__")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Map<String, Object>> nodeOutputs =
                                (Map<String, Map<String, Object>>) input.get("__nodeOutputs__");
                            if (nodeOutputs != null && nodeOutputs.containsKey(refNodeId)) {
                                Map<String, Object> nodeOutput = nodeOutputs.get(refNodeId);
                                refValue = nodeOutput.get(refParamName);
                            }
                        }

                        log.debug("引用参数 {} 的值: {}", refParamName, refValue);

                        if (refValue != null) {
                            paramValues.put(paramName, refValue.toString());
                        }
                    }
                }
            }
        }

        log.debug("参数值映射: {}", paramValues);

        // 替换模板中的 {{参数名}}
        String result = responseContent;
        Pattern pattern = Pattern.compile("\\{\\{(.*?)\\}\\}");
        Matcher matcher = pattern.matcher(responseContent);

        while (matcher.find()) {
            String paramName = matcher.group(1).trim();
            String paramValue = paramValues.getOrDefault(paramName, "");
            result = result.replace("{{" + paramName + "}}", paramValue);
        }

        log.debug("输出节点最终结果: {}", result);
        output.put("output", result);
        return output;
    }

    @Override
    public String getSupportedNodeType() {
        return "output";
    }
}
