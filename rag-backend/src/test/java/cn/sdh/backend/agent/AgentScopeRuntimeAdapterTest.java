package cn.sdh.backend.agent;

import cn.sdh.backend.agent.runtime.*;
import cn.sdh.backend.entity.AgentTool;
import cn.sdh.backend.entity.ModelConfig;
import io.agentscope.core.message.*;
import io.agentscope.core.model.*;
import io.agentscope.core.state.AgentStateStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentScopeRuntimeAdapterTest {
    private final AgentScopeModelFactory factory = mock(AgentScopeModelFactory.class);
    private final Model model = mock(Model.class);
    private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
    private final AgentStateStore states = stateStore();

    private AgentStateStore stateStore() {
        jdbc.execute("CREATE TABLE agent_runtime_state (user_id VARCHAR(64), session_id VARCHAR(100), "
                + "state_key VARCHAR(100), state_version BIGINT, payload LONGTEXT, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "PRIMARY KEY(user_id,session_id,state_key))");
        return new JdbcAgentStateStore(jdbc);
    }

    @Test void streamsActualModelDeltasAndReturnsFinalAnswer() {
        when(factory.create(any())).thenReturn(model);
        when(model.getModelName()).thenReturn("test-model");
        when(model.stream(anyList(), anyList(), any())).thenReturn(Flux.just(text("你"), text("好")));
        List<String> deltas = new ArrayList<>();
        var result = new AgentScopeRuntimeAdapter(factory, states).execute(request(List.of(), List.of(),
                (tool, id, args) -> { throw new AssertionError("No tool expected"); }, deltas));
        assertEquals(AgentRuntimePort.Status.COMPLETED, result.status());
        assertEquals("你好", result.answer());
        assertEquals("你好", String.join("", deltas));
        assertTrue(deltas.size() >= 2);
    }

    @Test void approvalSuspendsWithoutAnotherModelCallAndResumesWithOriginalToolResult() {
        when(factory.create(any())).thenReturn(model);
        when(model.getModelName()).thenReturn("test-model");
        AgentTool tool = new AgentTool();
        tool.setCode("create_ticket"); tool.setName("创建工单"); tool.setHttpMethod("POST");
        AtomicInteger executions = new AtomicInteger();
        when(model.stream(anyList(), anyList(), any())).thenReturn(Flux.just(ChatResponse.builder()
                .content(List.of(ToolUseBlock.builder().id("call-1").name(tool.getCode())
                        .input(Map.of("orderId", "42")).build())).build()));
        var suspended = new AgentScopeRuntimeAdapter(factory, states).execute(request(List.of(tool), List.of(),
                (t, id, args) -> {
                    executions.incrementAndGet();
                    assertEquals("call-1", id);
                    return new AgentRuntimePort.ToolReply("待审批", true);
                }, new ArrayList<>()));
        assertEquals(AgentRuntimePort.Status.SUSPENDED, suspended.status());
        verify(model, times(1)).stream(anyList(), anyList(), any());

        when(model.stream(anyList(), anyList(), any())).thenAnswer(call -> {
            List<Msg> messages = call.getArgument(0);
            assertTrue(messages.stream().flatMap(m -> m.getContent().stream())
                    .filter(ToolResultBlock.class::isInstance).map(ToolResultBlock.class::cast)
                    .anyMatch(block -> "call-1".equals(block.getId()) && block.getOutput().stream()
                            .filter(TextBlock.class::isInstance).map(TextBlock.class::cast)
                            .anyMatch(text -> text.getText().contains("ticket-42"))));
            return Flux.just(text("工单 ticket-42 已创建"));
        });
        // New runtime and store instances must restore serialized messages, not shared object references.
        var completed = new AgentScopeRuntimeAdapter(factory, new JdbcAgentStateStore(jdbc)).execute(request(List.of(tool),
                List.of(new AgentRuntimePort.ResumeResult("call-1", tool.getCode(), "ticket-42")),
                (t, id, args) -> { throw new AssertionError("Approved tool must not execute again"); }, new ArrayList<>()));
        assertEquals(AgentRuntimePort.Status.COMPLETED, completed.status());
        assertEquals(1, executions.get());
    }

    private AgentRuntimePort.Request request(List<AgentTool> tools, List<AgentRuntimePort.ResumeResult> results,
                                             AgentRuntimePort.ToolExecutor executor, List<String> deltas) {
        return new AgentRuntimePort.Request("run-1", 7L, "业务助手", "创建工单", new ModelConfig(),
                tools, 3, results, executor, deltas::add);
    }

    @Test void exhaustedRemainingBudgetDoesNotCreateOrCallAModel() {
        var request = new AgentRuntimePort.Request("run-1", 7L, "业务助手", "问题", new ModelConfig(),
                List.of(), 3, List.of(), (tool, id, args) -> { throw new AssertionError(); }, delta -> {},
                null, () -> {}, 0, usage -> {});
        assertThrows(cn.sdh.backend.common.exception.BusinessException.class,
                () -> new AgentScopeRuntimeAdapter(factory, states).execute(request));
        verifyNoInteractions(factory, model);
    }

    @Test void reportsUsageBeforeRejectingAnAttemptThatExceedsItsRemainingAllowance() {
        when(factory.create(any())).thenReturn(model);
        when(model.getModelName()).thenReturn("test-model");
        when(model.stream(anyList(), anyList(), any())).thenReturn(Flux.just(ChatResponse.builder()
                .content(List.of(TextBlock.builder().text("超额回答").build())).usage(new ChatUsage(4, 2, 0.1)).build()));
        List<AgentRuntimePort.Usage> persisted = new ArrayList<>();
        var request = new AgentRuntimePort.Request("run-1", 7L, "业务助手", "问题", new ModelConfig(),
                List.of(), 3, List.of(), (tool, id, args) -> { throw new AssertionError(); }, delta -> {},
                null, () -> {}, 5, persisted::add);
        assertThrows(cn.sdh.backend.common.exception.BusinessException.class,
                () -> new AgentScopeRuntimeAdapter(factory, states).execute(request));
        assertEquals(List.of(new AgentRuntimePort.Usage(1, 4, 2)), persisted);
        verify(model, times(1)).stream(anyList(), anyList(), any());
    }

    @Test void aFinalAnswerAtTheExactRemainingAllowanceCanComplete() {
        when(factory.create(any())).thenReturn(model);
        when(model.getModelName()).thenReturn("test-model");
        when(model.stream(anyList(), anyList(), any())).thenReturn(Flux.just(ChatResponse.builder()
                .content(List.of(TextBlock.builder().text("答案").build())).usage(new ChatUsage(3, 2, 0.1)).build()));
        var request = new AgentRuntimePort.Request("run-1", 7L, "业务助手", "问题", new ModelConfig(),
                List.of(), 3, List.of(), (tool, id, args) -> { throw new AssertionError(); }, delta -> {},
                null, () -> {}, 5, usage -> {});
        var result = new AgentScopeRuntimeAdapter(factory, states).execute(request);
        assertEquals(AgentRuntimePort.Status.COMPLETED, result.status());
        assertEquals(5, result.inputTokens() + result.outputTokens());
    }
    private ChatResponse text(String text) {
        return ChatResponse.builder().content(List.of(TextBlock.builder().text(text).build())).build();
    }
}
