package cn.sdh.backend.agent.runtime;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.AgentTool;
import com.alibaba.fastjson2.JSON;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.*;
import io.agentscope.core.message.*;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.permission.*;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.ConflictPolicy;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** One SDK instance per attempt; durable state is scoped to platform user + run. */
@Component
public class AgentScopeRuntimeAdapter implements AgentRuntimePort {
    private final AgentScopeModelFactory models;
    private final AgentStateStore states;

    @Autowired
    public AgentScopeRuntimeAdapter(AgentScopeModelFactory models, JdbcAgentStateStore states) {
        this(models, (AgentStateStore) states);
    }
    public AgentScopeRuntimeAdapter(AgentScopeModelFactory models, AgentStateStore states) {
        this.models = models;
        this.states = states;
    }

    @Override public Result execute(Request request) {
        request.checkpoint().run();
        if (request.remainingTokens() <= 0 || request.maxSteps() <= 0) throw new BusinessException(409, "运行预算已耗尽");
        AgentStateStore attemptStates = request.fence() == null ? states
                : ((JdbcAgentStateStore) states).fenced(request.fence());
        Toolkit toolkit = new Toolkit();
        var permissions = PermissionContextState.builder().mode(PermissionMode.DEFAULT);
        for (AgentTool tool : request.tools()) {
            toolkit.registerAgentTool(adapt(tool, request));
            permissions.addAllowRule(tool.getCode(), new PermissionRule(tool.getCode(), null,
                    PermissionBehavior.ALLOW, "platform-governed-tool"));
        }
        RuntimeContext context = RuntimeContext.builder().userId(request.userId().toString())
                .sessionId(request.runKey()).build();
        List<Msg> inputs = request.resumeResults().isEmpty()
                ? List.of(new UserMessage(request.question()))
                : List.of(new ToolResultMessage(request.resumeResults().stream()
                    .map(result -> ToolResultBlock.of(result.toolCallId(), result.toolCode(),
                            TextBlock.builder().text(result.value()).build())).toList()));
        int[] usage = new int[3];
        AtomicReference<Msg> finalMessage = new AtomicReference<>();
        try (ReActAgent agent = ReActAgent.builder().name("business-assistant")
                .sysPrompt(request.systemPrompt()).model(models.create(request.model()))
                .toolkit(toolkit).permissionContext(permissions.build())
                .stateStore(attemptStates).conflictPolicy(ConflictPolicy.FAIL)
                .maxIters(request.maxSteps())
                .modelExecutionConfig(ExecutionConfig.builder().maxAttempts(1).timeout(Duration.ofSeconds(120)).build())
                .toolExecutionConfig(ExecutionConfig.builder().maxAttempts(1).build()).build()) {
            agent.streamEvents(inputs, context).publishOn(Schedulers.boundedElastic())
                    .doOnNext(event -> {
                        request.checkpoint().run();
                        if (event instanceof TextBlockDeltaEvent text) request.textDelta().accept(text.getDelta());
                        if (event instanceof ModelCallEndEvent model) {
                            usage[0]++;
                            if (model.getUsage() != null) {
                                usage[1] = Math.addExact(usage[1], model.getUsage().getInputTokens());
                                usage[2] = Math.addExact(usage[2], model.getUsage().getOutputTokens());
                            }
                            request.usageObserver().accept(new Usage(usage[0], usage[1], usage[2]));
                            if ((long) usage[1] + usage[2] > request.remainingTokens()) {
                                throw new BusinessException(409, "超出运行 Token 预算");
                            }
                        }
                        if (event instanceof AgentResultEvent result) finalMessage.set(result.getResult());
                    }).blockLast(Duration.ofMinutes(10));
        }
        Msg result = finalMessage.get();
        if (result == null) throw new BusinessException(502, "AgentScope 未返回运行结果");
        Status status = switch (result.getGenerateReason()) {
            case TOOL_SUSPENDED, PERMISSION_ASKING -> Status.SUSPENDED;
            case MAX_ITERATIONS -> Status.LIMIT_REACHED;
            case INTERRUPTED -> Status.INTERRUPTED;
            case MODEL_STOP, STRUCTURED_OUTPUT -> Status.COMPLETED;
            default -> Status.INTERRUPTED;
        };
        return new Result(status, result.getTextContent(), usage[0], usage[1], usage[2]);
    }

    private io.agentscope.core.tool.AgentTool adapt(AgentTool tool, Request request) {
        return new io.agentscope.core.tool.AgentTool() {
            public String getName() { return tool.getCode(); }
            public String getDescription() { return tool.getDescription() == null ? tool.getName() : tool.getDescription(); }
            @SuppressWarnings("unchecked")
            public Map<String, Object> getParameters() {
                return tool.getInputSchema() == null || tool.getInputSchema().isBlank()
                        ? Map.of("type", "object") : JSON.parseObject(tool.getInputSchema(), Map.class);
            }
            public boolean isReadOnly() { return "GET".equalsIgnoreCase(tool.getHttpMethod()); }
            public Mono<ToolResultBlock> callAsync(io.agentscope.core.tool.ToolCallParam call) {
                return Mono.fromCallable(() -> {
                    request.checkpoint().run();
                    ToolReply reply = request.executor().execute(tool, call.getToolUseBlock().getId(), call.getInput());
                    return reply.suspended() ? ToolResultBlock.suspended(call.getToolUseBlock())
                            : ToolResultBlock.text(reply.value());
                }).subscribeOn(Schedulers.boundedElastic());
            }
        };
    }
}
