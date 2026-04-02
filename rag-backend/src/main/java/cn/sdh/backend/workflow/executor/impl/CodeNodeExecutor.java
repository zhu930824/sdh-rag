package cn.sdh.backend.workflow.executor.impl;

import cn.sdh.backend.workflow.executor.NodeExecutor;
import cn.sdh.backend.workflow.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.HashMap;
import java.util.Map;

/**
 * 代码节点执行器
 */
@Slf4j
@Component
public class CodeNodeExecutor implements NodeExecutor {

    private final ScriptEngineManager engineManager = new ScriptEngineManager();

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) {
        Map<String, Object> output = new HashMap<>();
        Map<String, Object> data = node.getData();

        if (data == null) {
            output.put("output", "代码节点未配置");
            return output;
        }

        // 获取配置
        String language = (String) data.get("language");
        String code = (String) data.get("code");

        if (code == null || code.isEmpty()) {
            output.put("output", "代码为空");
            return output;
        }

        language = language != null ? language.toLowerCase() : "javascript";

        log.info("代码节点配置 - language: {}", language);
        log.debug("代码内容: {}", code);

        try {
            if ("javascript".equals(language)) {
                output = executeJavaScript(code, input);
            } else if ("python".equals(language)) {
                // Python 需要额外的脚本引擎支持
                output.put("output", "Python 暂不支持，请使用 JavaScript");
                output.put("error", "Python engine not available");
            } else {
                output.put("output", "不支持的语言: " + language);
            }
        } catch (Exception e) {
            log.error("代码执行失败", e);
            output.put("output", "执行失败: " + e.getMessage());
            output.put("error", e.getMessage());
        }

        return output;
    }

    /**
     * 执行 JavaScript 代码
     */
    private Map<String, Object> executeJavaScript(String code, Map<String, Object> input) throws Exception {
        ScriptEngine engine = engineManager.getEngineByName("javascript");

        if (engine == null) {
            // 尝试使用 GraalJS
            engine = engineManager.getEngineByName("graal.js");
        }

        if (engine == null) {
            throw new RuntimeException("JavaScript 引擎不可用");
        }

        // 将输入数据注入到脚本上下文
        engine.put("input", input);

        // 包装代码，支持 return 语句
        String wrappedCode = "(function() {\n" +
            "    var __result = (function() {\n" +
            "        " + code + "\n" +
            "    })();\n" +
            "    if (typeof __result === 'undefined') {\n" +
            "        __result = {};\n" +
            "    }\n" +
            "    if (typeof __result !== 'object') {\n" +
            "        return { output: __result };\n" +
            "    }\n" +
            "    return __result;\n" +
            "})()";

        Object result = engine.eval(wrappedCode);

        Map<String, Object> output = new HashMap<>();
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            output.putAll(resultMap);
        } else {
            output.put("output", result != null ? result.toString() : null);
        }

        return output;
    }

    @Override
    public String getSupportedNodeType() {
        return "code";
    }
}
