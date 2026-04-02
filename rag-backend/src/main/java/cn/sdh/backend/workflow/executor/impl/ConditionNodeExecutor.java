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

/**
 * 条件节点执行器
 */
@Slf4j
@Component
public class ConditionNodeExecutor implements NodeExecutor {

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) {
        Map<String, Object> output = new HashMap<>();
        Map<String, Object> data = node.getData();

        if (data == null) {
            output.put("branch", "default");
            return output;
        }

        // 获取条件列表
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) data.get("conditions");

        if (conditions == null || conditions.isEmpty()) {
            output.put("branch", "default");
            return output;
        }

        log.debug("条件节点输入: {}", input);

        // 评估每个条件
        for (Map<String, Object> condition : conditions) {
            String expression = (String) condition.get("expression");
            String label = (String) condition.get("label");

            if (expression == null || label == null) {
                continue;
            }

            try {
                boolean result = evaluateCondition(expression, input);
                log.debug("条件 '{}' ({}) 评估结果: {}", label, expression, result);

                if (result) {
                    output.put("branch", label);
                    output.put("matchedCondition", expression);
                    log.info("条件匹配: {} -> {}", expression, label);
                    return output;
                }
            } catch (Exception e) {
                log.warn("条件评估失败: {} - {}", expression, e.getMessage());
            }
        }

        // 没有匹配的条件，返回默认分支
        output.put("branch", "default");
        log.info("没有条件匹配，使用默认分支");
        return output;
    }

    /**
     * 评估条件表达式
     */
    private boolean evaluateCondition(String expression, Map<String, Object> input) {
        // 简单的条件评估引擎
        // 支持: true, false, 变量比较, 简单的逻辑运算

        expression = expression.trim();

        // 处理字面量
        if ("true".equalsIgnoreCase(expression)) {
            return true;
        }
        if ("false".equalsIgnoreCase(expression)) {
            return false;
        }

        // 替换变量引用
        String evaluated = expression;

        // 替换 input.xxx 格式的变量
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("input\\.(\\w+)");
        java.util.regex.Matcher matcher = pattern.matcher(evaluated);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = input.get(varName);
            String replacement;
            if (value == null) {
                replacement = "null";
            } else if (value instanceof Number) {
                replacement = value.toString();
            } else if (value instanceof Boolean) {
                replacement = value.toString();
            } else {
                replacement = "\"" + value.toString().replace("\"", "\\\"") + "\"";
            }
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        evaluated = sb.toString();

        log.debug("评估表达式: {} -> {}", expression, evaluated);

        // 使用简单的表达式解析
        return evaluateSimpleExpression(evaluated);
    }

    /**
     * 简单表达式评估
     */
    private boolean evaluateSimpleExpression(String expr) {
        // 处理比较运算符
        if (expr.contains(">")) {
            String[] parts = expr.split(">");
            if (parts.length == 2) {
                double left = parseNumber(parts[0].trim());
                double right = parseNumber(parts[1].trim());
                return left > right;
            }
        }
        if (expr.contains("<")) {
            String[] parts = expr.split("<");
            if (parts.length == 2) {
                double left = parseNumber(parts[0].trim());
                double right = parseNumber(parts[1].trim());
                return left < right;
            }
        }
        if (expr.contains(">=")) {
            String[] parts = expr.split(">=");
            if (parts.length == 2) {
                double left = parseNumber(parts[0].trim());
                double right = parseNumber(parts[1].trim());
                return left >= right;
            }
        }
        if (expr.contains("<=")) {
            String[] parts = expr.split("<=");
            if (parts.length == 2) {
                double left = parseNumber(parts[0].trim());
                double right = parseNumber(parts[1].trim());
                return left <= right;
            }
        }
        if (expr.contains("==")) {
            String[] parts = expr.split("==");
            if (parts.length == 2) {
                return parts[0].trim().equals(parts[1].trim());
            }
        }
        if (expr.contains("!=")) {
            String[] parts = expr.split("!=");
            if (parts.length == 2) {
                return !parts[0].trim().equals(parts[1].trim());
            }
        }

        // 默认返回 false
        return false;
    }

    private double parseNumber(String s) {
        try {
            s = s.replace("\"", "").trim();
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public String getSupportedNodeType() {
        return "condition";
    }
}
