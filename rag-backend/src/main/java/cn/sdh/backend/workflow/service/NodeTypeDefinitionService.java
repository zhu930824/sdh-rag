package cn.sdh.backend.workflow.service;

import cn.sdh.backend.workflow.dto.NodeTypeDefinition;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 节点类型定义服务
 */
@Service
public class NodeTypeDefinitionService {

    /**
     * 获取所有节点类型定义
     */
    public List<NodeTypeDefinition> getAllNodeTypeDefinitions() {
        List<NodeTypeDefinition> definitions = new ArrayList<>();

        // 基础节点
        definitions.add(createInputNodeDefinition());
        definitions.add(createOutputNodeDefinition());

        // LLM 相关
        definitions.add(createLlmNodeDefinition());
        definitions.add(createRetrievalNodeDefinition());
        definitions.add(createRerankNodeDefinition());

        // 逻辑节点
        definitions.add(createConditionNodeDefinition());

        // 工具节点
        definitions.add(createHttpNodeDefinition());
        definitions.add(createCodeNodeDefinition());

        return definitions;
    }

    /**
     * 获取单个节点类型定义
     */
    public NodeTypeDefinition getNodeTypeDefinition(String type) {
        return getAllNodeTypeDefinitions().stream()
                .filter(def -> def.getType().equals(type))
                .findFirst()
                .orElse(null);
    }

    private NodeTypeDefinition createInputNodeDefinition() {
        NodeTypeDefinition def = new NodeTypeDefinition();
        def.setType("input");
        def.setName("输入节点");
        def.setIcon("EditOutlined");
        def.setCategory("basic");
        def.setDescription("工作流的输入参数定义节点，用于接收外部输入数据");

        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("variables", List.of(
                Map.of("name", "query", "type", "string", "defaultValue", "")
        ));
        def.setDefaultConfig(defaultConfig);

        def.setInputs(List.of());
        def.setOutputs(List.of(
                createParam("query", "string", true, "用户输入的查询"),
                createParam("input", "string", false, "原始输入内容")
        ));
        return def;
    }

    private NodeTypeDefinition createOutputNodeDefinition() {
        NodeTypeDefinition def = new NodeTypeDefinition();
        def.setType("output");
        def.setName("输出节点");
        def.setIcon("SendOutlined");
        def.setCategory("basic");
        def.setDescription("工作流的输出节点，定义最终输出格式");

        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("outputParams", List.of());
        defaultConfig.put("responseContent", "");
        def.setDefaultConfig(defaultConfig);

        def.setInputs(List.of(
                createParam("output", "string", false, "要输出的内容")
        ));
        def.setOutputs(List.of(
                createParam("output", "string", true, "最终输出结果")
        ));
        return def;
    }

    private NodeTypeDefinition createLlmNodeDefinition() {
        NodeTypeDefinition def = new NodeTypeDefinition();
        def.setType("llm");
        def.setName("LLM 节点");
        def.setIcon("RobotOutlined");
        def.setCategory("llm");
        def.setDescription("大语言模型节点，用于调用LLM进行文本生成");

        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("model", "qwen-max");
        defaultConfig.put("systemPrompt", "你是一个有帮助的AI助手。");
        defaultConfig.put("prompt", "{{query}}");
        defaultConfig.put("temperature", 0.7);
        defaultConfig.put("maxTokens", 2000);
        defaultConfig.put("inputParams", List.of());
        def.setDefaultConfig(defaultConfig);

        def.setInputs(List.of(
                createParam("query", "string", true, "提示词或查询"),
                createParam("systemPrompt", "string", false, "系统提示词"),
                createParam("context", "string", false, "上下文信息")
        ));
        def.setOutputs(List.of(
                createParam("output", "string", true, "LLM 生成的文本"),
                createParam("tokens", "number", false, "使用的Token数量")
        ));
        return def;
    }

    private NodeTypeDefinition createRetrievalNodeDefinition() {
        NodeTypeDefinition def = new NodeTypeDefinition();
        def.setType("retrieval");
        def.setName("知识检索节点");
        def.setIcon("SearchOutlined");
        def.setCategory("llm");
        def.setDescription("从知识库检索相关文档片段");

        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("knowledgeBaseId", null);
        defaultConfig.put("topK", 5);
        defaultConfig.put("scoreThreshold", 0.7);
        def.setDefaultConfig(defaultConfig);

        def.setInputs(List.of(
                createParam("query", "string", true, "检索查询文本")
        ));
        def.setOutputs(List.of(
                createParam("documents", "array", true, "检索到的文档列表"),
                createParam("context", "string", true, "拼接后的上下文文本")
        ));
        return def;
    }

    private NodeTypeDefinition createRerankNodeDefinition() {
        NodeTypeDefinition def = new NodeTypeDefinition();
        def.setType("rerank");
        def.setName("重排序节点");
        def.setIcon("SortAscendingOutlined");
        def.setCategory("llm");
        def.setDescription("对检索结果进行重排序，提高相关性");

        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("model", "qwen3-rerank");
        defaultConfig.put("topK", 5);
        def.setDefaultConfig(defaultConfig);

        def.setInputs(List.of(
                createParam("documents", "array", true, "待重排序的文档列表"),
                createParam("query", "string", true, "查询文本")
        ));
        def.setOutputs(List.of(
                createParam("documents", "array", true, "重排序后的文档列表"),
                createParam("context", "string", true, "拼接后的上下文文本")
        ));
        return def;
    }

    private NodeTypeDefinition createConditionNodeDefinition() {
        NodeTypeDefinition def = new NodeTypeDefinition();
        def.setType("condition");
        def.setName("条件节点");
        def.setIcon("ForkOutlined");
        def.setCategory("logic");
        def.setDescription("条件分支节点，根据条件执行不同的分支");

        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("conditions", List.of(
                Map.of("expression", "true", "label", "默认")
        ));
        def.setDefaultConfig(defaultConfig);

        def.setInputs(List.of(
                createParam("input", "object", true, "输入数据")
        ));
        def.setOutputs(List.of(
                createParam("branch", "string", true, "匹配的分支标识"),
                createParam("matchedCondition", "string", false, "匹配的条件表达式")
        ));
        return def;
    }

    private NodeTypeDefinition createHttpNodeDefinition() {
        NodeTypeDefinition def = new NodeTypeDefinition();
        def.setType("http");
        def.setName("HTTP 请求节点");
        def.setIcon("ApiOutlined");
        def.setCategory("tool");
        def.setDescription("发送HTTP请求，支持GET/POST/PUT/DELETE方法");

        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("method", "GET");
        defaultConfig.put("url", "");
        defaultConfig.put("headers", "{}");
        defaultConfig.put("body", "");
        def.setDefaultConfig(defaultConfig);

        def.setInputs(List.of(
                createParam("url", "string", true, "请求URL"),
                createParam("body", "object", false, "请求体")
        ));
        def.setOutputs(List.of(
                createParam("response", "string", true, "响应内容"),
                createParam("status", "number", true, "HTTP状态码")
        ));
        return def;
    }

    private NodeTypeDefinition createCodeNodeDefinition() {
        NodeTypeDefinition def = new NodeTypeDefinition();
        def.setType("code");
        def.setName("代码节点");
        def.setIcon("CodeOutlined");
        def.setCategory("tool");
        def.setDescription("执行JavaScript代码进行数据处理");

        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("language", "javascript");
        defaultConfig.put("code", "// 输入变量: input\n// 返回结果对象\nreturn { output: input };");
        def.setDefaultConfig(defaultConfig);

        def.setInputs(List.of(
                createParam("input", "object", true, "输入数据")
        ));
        def.setOutputs(List.of(
                createParam("output", "object", true, "代码执行结果")
        ));
        return def;
    }

    private NodeTypeDefinition.ParamDefinition createParam(String name, String type, boolean required, String description) {
        NodeTypeDefinition.ParamDefinition param = new NodeTypeDefinition.ParamDefinition();
        param.setName(name);
        param.setType(type);
        param.setRequired(required);
        param.setDescription(description);
        return param;
    }
}
