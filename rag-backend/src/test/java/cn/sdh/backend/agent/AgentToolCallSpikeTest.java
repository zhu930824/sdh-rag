package cn.sdh.backend.agent;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * M1 spike:验证 DashScope 非流式 call + ToolCallback 内部工具循环是否可用(决策轮为 Agent 核心机制)。
 * 纯单元测试,不启动 Spring 上下文、不落库。
 * 需要环境变量 DASHSCOPE_API_KEY 才会真正执行;未配置时自动跳过。
 * 可选:DASHSCOPE_BASE_URL(默认 dashscope.aliyuncs.com)、DASHSCOPE_MODEL(默认 qwen-plus)。
 */
class AgentToolCallSpikeTest {

    private String apiKey() {
        String key = System.getenv("DASHSCOPE_API_KEY");
        Assumptions.assumeTrue(key != null && !key.isEmpty(),
                "未配置 DASHSCOPE_API_KEY,跳过 DashScope 工具调用 spike");
        return key;
    }

    private DashScopeChatModel model(String apiKey) {
        String baseUrl = System.getenv("DASHSCOPE_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://dashscope.aliyuncs.com";
        }
        String modelName = System.getenv("DASHSCOPE_MODEL");
        if (modelName == null || modelName.isEmpty()) {
            modelName = "qwen-plus";
        }
        DashScopeApi api = DashScopeApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .model(modelName)
                .temperature(0.3)
                .build();
        return DashScopeChatModel.builder().dashScopeApi(api).defaultOptions(options).build();
    }

    private ToolCallback tool(String name, String description, String schema, Function<String, String> fn,
                              AtomicInteger counter) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name(name)
                        .description(description)
                        .inputSchema(schema)
                        .build();
            }

            @Override
            public String call(String toolInput) {
                counter.incrementAndGet();
                return fn.apply(toolInput);
            }
        };
    }

    private String schema(String... requiredProps) {
        StringBuilder props = new StringBuilder();
        for (String p : requiredProps) {
            props.append(",\"").append(p).append("\":{\"type\":\"string\"}");
        }
        String required = String.join("\",\"", requiredProps);
        return "{\"type\":\"object\",\"properties\":{" + props.substring(Math.min(1, props.length()))
                + "},\"required\":[\"" + required + "\"]}";
    }

    /**
     * 场景 1:模型自主连续调用两个工具并依据结果作答(验证内部工具循环 + 动态 ToolCallback 注册)。
     */
    @Test
    void dashScopeInternalToolLoop() {
        DashScopeChatModel model = model(apiKey());
        AtomicInteger orderCalls = new AtomicInteger();
        AtomicInteger stockCalls = new AtomicInteger();

        ToolCallback queryOrder = tool("query_order", "按客户名查询最近订单,返回订单号/金额/商品/状态",
                schema("customer"),
                args -> "{\"orders\":[{\"orderNo\":\"SO20260701001\",\"customer\":\"张三\","
                        + "\"amount\":1299,\"product\":\"智能手表\",\"status\":\"DELIVERED\"}]}",
                orderCalls);
        ToolCallback queryStock = tool("query_stock", "按商品名查询库存",
                schema("product"),
                args -> "{\"product\":\"智能手表\",\"stock\":42}",
                stockCalls);

        String system = "你是客服助手。回答前若信息不足,先调用提供的工具获取真实数据,严禁编造。"
                + "查询订单后用工具查库存也可以,但不要重复调用同一工具超过一次。";
        Prompt prompt = new Prompt(List.of(new SystemMessage(system),
                new UserMessage("请帮我查一下客户张三最近一笔订单是什么,金额多少,该商品当前库存多少?")),
                DashScopeChatOptions.builder()
                        .toolCallbacks(List.of(queryOrder, queryStock))
                        .internalToolExecutionEnabled(true)
                        .build());

        ChatResponse response = model.call(prompt);
        String text = response.getResult().getOutput().getText();
        System.out.println("[spike-1] 工具调用次数 order=" + orderCalls.get() + " stock=" + stockCalls.get());
        System.out.println("[spike-1] 模型最终回答: " + text);

        // 内部循环应真实执行工具(至少各一次),且最终回答基于工具结果
        if (orderCalls.get() == 0 || stockCalls.get() == 0) {
            throw new AssertionError("模型未调用工具 order=" + orderCalls.get() + " stock=" + stockCalls.get());
        }
        if (text == null || text.isBlank()) {
            throw new AssertionError("模型最终回答为空");
        }
    }

    /**
     * 场景 2(核心风险验证):工具返回"审批暂停"标记后,模型是否停止循环不再重复请求工具。
     * Agent 审批闸依赖此行为:见 __agent_pause__ 后收尾,而非无休止重试。
     */
    @Test
    void dashScopeStopsLoopOnPauseMarker() {
        DashScopeChatModel model = model(apiKey());
        AtomicInteger calls = new AtomicInteger();

        ToolCallback createTicket = tool("create_service_ticket", "为客户创建售后工单,需要审批",
                schema("customer", "orderNo", "title"),
                args -> "{\"__agent_pause__\":12345,\"actionId\":12345,\"message\":\"需要人工审批,已暂停\"}",
                calls);

        String system = "你是客服助手。规则:当工具返回 JSON 中含有 __agent_pause__ 字段时,表示该操作需要人工审批、"
                + "已被系统暂停。此时严禁再次调用任何工具,也不要虚构执行结果,直接回复:该操作正在等待人工审批。";
        Prompt prompt = new Prompt(List.of(new SystemMessage(system),
                new UserMessage("客户张三的订单 SO20260701001 屏幕碎了,请为他创建售后工单")),
                DashScopeChatOptions.builder()
                        .toolCallbacks(List.of(createTicket))
                        .internalToolExecutionEnabled(true)
                        .build());

        ChatResponse response = model.call(prompt);
        String text = response.getResult().getOutput().getText();
        System.out.println("[spike-2] 工具调用次数=" + calls.get());
        System.out.println("[spike-2] 模型最终回答: " + text);

        if (calls.get() == 0) {
            throw new AssertionError("模型未请求需要审批的工具(场景前提失效)");
        }
        if (calls.get() > 3) {
            throw new AssertionError("模型在审批标记后仍反复调用工具 " + calls.get() + " 次,内部循环无法截停");
        }
    }
}
