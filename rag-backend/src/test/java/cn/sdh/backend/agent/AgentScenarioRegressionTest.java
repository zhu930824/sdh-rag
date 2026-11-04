package cn.sdh.backend.agent;

import cn.sdh.backend.agent.runtime.*;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.AgentTool;
import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.job.PlatformJobCancelledException;
import com.alibaba.fastjson2.JSON;
import io.agentscope.core.message.*;
import io.agentscope.core.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Deterministic SDK contract gate. Model responses are scripted; the runtime and state store are real. */
class AgentScenarioRegressionTest {
    enum Kind { ANSWER, TOOL, RESUME, BUDGET, ISOLATION, CANCEL, MODEL_FAILURE }
    record Scenario(String id, Kind kind, String question, List<String> chunks, String tool, String method,
                    Map<String, Object> arguments, long remainingTokens, int maxSteps, String expected,
                    Long userId, String runKey, int checkpoint) {
        @Override public String toString() { return id; }
    }

    static List<Scenario> dataset() throws Exception {
        try (var input = AgentScenarioRegressionTest.class.getResourceAsStream("/agent-regression/scenarios.json")) {
            assertNotNull(input, "Regression dataset must be packaged on the test classpath");
            return JSON.parseArray(new String(input.readAllBytes(), StandardCharsets.UTF_8), Scenario.class);
        }
    }

    static Stream<Scenario> scenarios() throws Exception { return dataset().stream(); }

    @Test void datasetKeepsStableIdsAndAllRequiredCategories() throws Exception {
        var cases = dataset();
        assertTrue(cases.size() >= 30, "Do not silently shrink the release gate");
        assertEquals(cases.size(), cases.stream().map(Scenario::id).distinct().count());
        assertTrue(cases.stream().allMatch(c -> c.id() != null && !c.id().isBlank() && c.kind() != null));
        assertEquals(EnumSet.allOf(Kind.class), EnumSet.copyOf(cases.stream().map(Scenario::kind).toList()));
    }

    @ParameterizedTest(name = "{0}") @MethodSource("scenarios")
    void scenario(Scenario scenario) {
        try (var fixture = new Fixture()) {
            switch (scenario.kind()) {
                case ANSWER -> fixture.answer(scenario);
                case TOOL -> fixture.tool(scenario, false);
                case RESUME -> fixture.tool(scenario, true);
                case BUDGET -> fixture.budget(scenario);
                case ISOLATION -> fixture.isolation(scenario);
                case CANCEL -> fixture.cancel(scenario);
                case MODEL_FAILURE -> fixture.modelFailure(scenario);
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final AgentScopeModelFactory factory = mock(AgentScopeModelFactory.class);
        final Model model = mock(Model.class);
        final SingleConnectionDataSource database = new SingleConnectionDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "", true);
        final JdbcTemplate jdbc = new JdbcTemplate(database);
        final List<String> deltas = new ArrayList<>();
        final List<AgentRuntimePort.Usage> usages = new ArrayList<>();
        final AtomicInteger toolCalls = new AtomicInteger();
        final AgentRuntimePort.ToolExecutor noTools = (tool, id, args) -> {
            throw new AssertionError("Unexpected tool execution: " + tool.getCode());
        };

        Fixture() {
            // A case owns its connection and closes it after the SDK stream has terminated.
            jdbc.execute("CREATE TABLE agent_runtime_state (user_id VARCHAR(64), session_id VARCHAR(100), "
                    + "state_key VARCHAR(100), state_version BIGINT, payload LONGTEXT, "
                    + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(user_id,session_id,state_key))");
            when(factory.create(any())).thenReturn(model);
            when(model.getModelName()).thenReturn("scripted-regression-model");
        }

        @Override public void close() { database.destroy(); }

        AgentRuntimePort.Result execute(String run, Long user, String question, List<AgentTool> tools,
                                        List<AgentRuntimePort.ResumeResult> resume, AgentRuntimePort.ToolExecutor executor,
                                        int steps, long tokens, Runnable checkpoint) {
            // Recreate both instances on each attempt to exercise serialized restore after suspension.
            return new AgentScopeRuntimeAdapter(factory, new JdbcAgentStateStore(jdbc)).execute(
                    new AgentRuntimePort.Request(run, user, "业务助手", question, new ModelConfig(), tools, steps,
                            resume, executor, deltas::add, null, checkpoint, tokens, usages::add));
        }

        AgentRuntimePort.Result execute(List<AgentTool> tools, List<AgentRuntimePort.ResumeResult> resume,
                                        AgentRuntimePort.ToolExecutor executor) {
            return execute("run-1", 7L, "查询业务信息", tools, resume, executor, 3, 100, () -> {});
        }

        void answer(Scenario scenario) {
            when(model.stream(anyList(), anyList(), any())).thenAnswer(call -> {
                List<Msg> messages = call.getArgument(0);
                assertTrue(messages.stream().anyMatch(m -> scenario.question().equals(m.getTextContent())));
                return Flux.fromIterable(scenario.chunks()).map(AgentScenarioRegressionTest::text);
            });
            var result = execute("run-1", 7L, scenario.question(), List.of(), List.of(), noTools, 3, 100, () -> {});
            assertEquals(AgentRuntimePort.Status.COMPLETED, result.status());
            assertEquals(String.join("", scenario.chunks()), result.answer());
            assertEquals(result.answer(), String.join("", deltas));
            assertEquals(1, result.modelCalls());
            verify(model, times(1)).stream(anyList(), anyList(), any());
        }

        void tool(Scenario scenario, boolean suspend) {
            AgentTool tool = new AgentTool();
            tool.setCode(scenario.tool()); tool.setName(scenario.tool()); tool.setHttpMethod(scenario.method());
            when(model.stream(anyList(), anyList(), any())).thenReturn(Flux.just(ChatResponse.builder()
                    .content(List.of(ToolUseBlock.builder().id("call-42").name(scenario.tool())
                            .input(scenario.arguments()).build())).build()));
            AgentRuntimePort.ToolExecutor executor = (actual, callId, args) -> {
                assertEquals(scenario.tool(), actual.getCode());
                assertEquals("call-42", callId);
                assertEquals(scenario.arguments(), args);
                toolCalls.incrementAndGet();
                return new AgentRuntimePort.ToolReply("result-42", suspend);
            };
            if (suspend) {
                var waiting = execute(List.of(tool), List.of(), executor);
                assertEquals(AgentRuntimePort.Status.SUSPENDED, waiting.status());
                verify(model, times(1)).stream(anyList(), anyList(), any());
            }
            // The next model call must see the original call ID and the persisted/result-injected tool output.
            var invocation = new AtomicInteger();
            when(model.stream(anyList(), anyList(), any())).thenAnswer(call -> {
                if (!suspend && invocation.getAndIncrement() == 0) {
                    return Flux.just(ChatResponse.builder().content(List.of(ToolUseBlock.builder()
                            .id("call-42").name(scenario.tool()).input(scenario.arguments()).build())).build());
                }
                List<Msg> messages = call.getArgument(0);
                assertTrue(messages.stream().flatMap(m -> m.getContent().stream())
                        .filter(ToolResultBlock.class::isInstance).map(ToolResultBlock.class::cast)
                        .anyMatch(r -> "call-42".equals(r.getId()) && r.getOutput().stream()
                                .filter(TextBlock.class::isInstance).map(TextBlock.class::cast)
                                .anyMatch(t -> "result-42".equals(t.getText()))));
                return Flux.just(text("业务操作已完成"));
            });
            var result = execute(List.of(tool), suspend
                    ? List.of(new AgentRuntimePort.ResumeResult("call-42", scenario.tool(), "result-42")) : List.of(),
                    suspend ? noTools : executor);
            assertEquals(AgentRuntimePort.Status.COMPLETED, result.status());
            assertEquals("业务操作已完成", result.answer());
            assertEquals(1, toolCalls.get(), "Resuming must not repeat the external side effect");
            verify(model, times(2)).stream(anyList(), anyList(), any());
        }

        void budget(Scenario scenario) {
            when(model.stream(anyList(), anyList(), any())).thenReturn(Flux.just(ChatResponse.builder()
                    .content(List.of(TextBlock.builder().text("答案").build())).usage(new ChatUsage(3, 2, 0.1)).build()));
            var operation = (org.junit.jupiter.api.function.Executable) () -> execute("run-1", 7L, "查询", List.of(),
                    List.of(), noTools, scenario.maxSteps(), scenario.remainingTokens(), () -> {});
            if ("REJECTED".equals(scenario.expected())) assertHasCause(BusinessException.class, operation);
            else {
                var result = execute("run-1", 7L, "查询", List.of(), List.of(), noTools,
                        scenario.maxSteps(), scenario.remainingTokens(), () -> {});
                assertEquals(AgentRuntimePort.Status.COMPLETED, result.status());
                assertEquals("答案", result.answer());
            }
            if (scenario.maxSteps() <= 0 || scenario.remainingTokens() <= 0) {
                verify(factory, never()).create(any());
                verify(model, never()).stream(anyList(), anyList(), any());
                assertTrue(usages.isEmpty());
            } else {
                assertEquals(List.of(new AgentRuntimePort.Usage(1, 3, 2)), usages);
                verify(model, times(1)).stream(anyList(), anyList(), any());
            }
        }

        void isolation(Scenario scenario) {
            when(model.stream(anyList(), anyList(), any())).thenReturn(Flux.just(text("private-answer-42")));
            execute("run-1", 7L, "private-question-42", List.of(), List.of(), noTools, 3, 100, () -> {});
            when(model.stream(anyList(), anyList(), any())).thenAnswer(call -> {
                List<Msg> messages = call.getArgument(0);
                assertTrue(messages.stream().noneMatch(m -> m.getTextContent().contains("private-")),
                        "A different user or run must not inherit another conversation");
                return Flux.just(text("隔离后的回答"));
            });
            var result = execute(scenario.runKey(), scenario.userId(), "新问题", List.of(), List.of(), noTools, 3, 100, () -> {});
            assertEquals("隔离后的回答", result.answer());
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(DISTINCT CONCAT(user_id, ':', session_id)) FROM agent_runtime_state", Integer.class));
        }

        void cancel(Scenario scenario) {
            when(model.stream(anyList(), anyList(), any())).thenReturn(Flux.just(text("回答")));
            var checkpoints = new AtomicInteger();
            assertHasCause(PlatformJobCancelledException.class, () -> execute("run-1", 7L, "查询", List.of(),
                    List.of(), noTools, 3, 100, () -> {
                        if (checkpoints.incrementAndGet() >= scenario.checkpoint()) throw new PlatformJobCancelledException();
                    }));
            assertTrue(deltas.isEmpty(), "Cancellation must stop text publication at the checkpoint");
            if (scenario.checkpoint() == 1) verify(factory, never()).create(any());
        }

        void modelFailure(Scenario scenario) {
            when(model.stream(anyList(), anyList(), any())).thenReturn(Flux.concat(
                    Flux.fromIterable(scenario.chunks()).map(AgentScenarioRegressionTest::text),
                    Flux.error(new IllegalStateException("scripted-provider-failure"))));
            var error = assertHasCause(IllegalStateException.class, () -> execute(List.of(), List.of(), noTools));
            assertEquals("scripted-provider-failure", error.getMessage());
            verify(model, times(1)).stream(anyList(), anyList(), any());
        }
    }

    static ChatResponse text(String value) {
        return ChatResponse.builder().content(List.of(TextBlock.builder().text(value).build())).build();
    }

    static <T extends Throwable> T assertHasCause(Class<T> type, org.junit.jupiter.api.function.Executable operation) {
        Throwable error = assertThrows(RuntimeException.class, operation);
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) return type.cast(cause);
        }
        throw new AssertionError("Expected cause " + type.getName(), error);
    }
}
