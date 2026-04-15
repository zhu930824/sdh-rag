package cn.sdh.backend.workflow.executor.impl;

import cn.sdh.backend.workflow.executor.NodeExecutor;
import cn.sdh.backend.workflow.model.WorkflowNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 输入节点执行器
 */
@Component
public class InputNodeExecutor implements NodeExecutor {

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) {
        // 输入节点直接返回输入数据（排除内部字段）
        Map<String, Object> output = new HashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            // 排除内部字段，避免循环引用
            if (!entry.getKey().startsWith("__") && !entry.getKey().endsWith("__")) {
                output.put(entry.getKey(), entry.getValue());
            }
        }

        // 从节点配置获取变量定义，设置默认值
        Map<String, Object> data = node.getData();
        if (data != null && data.containsKey("variables")) {
            Object variablesObj = data.get("variables");
            if (variablesObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> variables = (List<Map<String, Object>>) variablesObj;
                for (Map<String, Object> variable : variables) {
                    String name = (String) variable.get("name");
                    Object defaultValue = variable.get("defaultValue");
                    if (name != null && !output.containsKey(name) && defaultValue != null) {
                        output.put(name, defaultValue);
                    }
                }
            }
        }

        return output;
    }

    @Override
    public String getSupportedNodeType() {
        return "input";
    }
}
