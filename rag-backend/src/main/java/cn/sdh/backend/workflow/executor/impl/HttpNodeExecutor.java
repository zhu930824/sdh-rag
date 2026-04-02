package cn.sdh.backend.workflow.executor.impl;

import cn.sdh.backend.workflow.executor.NodeExecutor;
import cn.sdh.backend.workflow.model.WorkflowNode;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 节点执行器
 */
@Slf4j
@Component
public class HttpNodeExecutor implements NodeExecutor {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) {
        Map<String, Object> output = new HashMap<>();
        Map<String, Object> data = node.getData();

        if (data == null) {
            output.put("response", "HTTP 节点未配置");
            output.put("status", 0);
            return output;
        }

        // 获取配置
        String method = (String) data.get("method");
        String url = (String) data.get("url");
        String headersStr = (String) data.get("headers");
        String body = (String) data.get("body");

        if (url == null || url.isEmpty()) {
            output.put("response", "URL 未配置");
            output.put("status", 0);
            return output;
        }

        // 替换 URL 中的变量
        url = replaceVariables(url, input);

        method = method != null ? method.toUpperCase() : "GET";

        log.info("HTTP 节点配置 - method: {}, url: {}", method, url);

        try {
            // 解析请求头
            HttpHeaders headers = new HttpHeaders();
            if (headersStr != null && !headersStr.isEmpty()) {
                try {
                    Map<String, String> headersMap = JSON.parseObject(headersStr, Map.class);
                    headersMap.forEach(headers::add);
                } catch (Exception e) {
                    log.warn("解析请求头失败: {}", e.getMessage());
                }
            }

            // 构建请求实体
            HttpEntity<String> entity;
            if ("GET".equals(method)) {
                headers.setContentType(MediaType.APPLICATION_JSON);
                entity = new HttpEntity<>(headers);
            } else {
                // 替换请求体中的变量
                if (body != null) {
                    body = replaceVariables(body, input);
                }
                entity = new HttpEntity<>(body, headers);
            }

            // 发送请求
            ResponseEntity<String> response;
            switch (method) {
                case "POST":
                    response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                    break;
                case "PUT":
                    response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
                    break;
                case "DELETE":
                    response = restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
                    break;
                default:
                    response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            }

            output.put("status", response.getStatusCode().value());
            output.put("response", response.getBody());

            log.debug("HTTP 响应状态: {}", response.getStatusCode());

        } catch (Exception e) {
            log.error("HTTP 请求失败", e);
            output.put("status", 0);
            output.put("response", "请求失败: " + e.getMessage());
            output.put("error", e.getMessage());
        }

        return output;
    }

    /**
     * 替换字符串中的变量引用
     */
    private String replaceVariables(String template, Map<String, Object> input) {
        if (template == null) return null;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\\{(.*?)\\}\\}");
        java.util.regex.Matcher matcher = pattern.matcher(template);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            Object value = input.get(varName);
            String replacement = value != null ? value.toString() : "";
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    @Override
    public String getSupportedNodeType() {
        return "http";
    }
}
