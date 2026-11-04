package cn.sdh.backend.agent;

import cn.sdh.backend.agent.runtime.*;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.*;
import cn.sdh.backend.service.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AgentAskServiceBudgetTest {
    @Test void reportedUsageSurvivesRuntimeFailureAndKeepsTheUnattributedHistoricalTotal() {
        var states = mock(RunStateService.class);
        var leases = mock(RunExecutionLeaseService.class);
        var lease = mock(RunExecutionLeaseService.Lease.class);
        var runtime = mock(AgentScopeRuntimeAdapter.class);
        var actions = mock(AgentActionService.class);
        var service = new AgentAskService(null, actions, null, states, null, null, runtime, null, leases, null, new AgentRunLifecycle(states), mock(AgentExecutionAttemptService.class));
        AgentRun persisted = new AgentRun(); persisted.setId(10L); persisted.setRunKey("run-1"); persisted.setEngine("agentscope");
        persisted.setStatus("RUNNING"); persisted.setUserId(7L); persisted.setMaxSteps(8); persisted.setStepCount(2);
        persisted.setPromptTokens(100); persisted.setCompletionTokens(50); persisted.setTotalTokens(200);
        when(states.getByRunKey("run-1")).thenReturn(persisted);
        when(leases.claim(eq("run-1"), eq(7L), any())).thenReturn(lease);
        when(lease.fence()).thenReturn(new RunExecutionLeaseService.Fence(10, "run-1", 7L, "worker", 1));
        when(states.guarded(any(), any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(1)).get());
        doAnswer(call -> {
            AgentRun progress = call.getArgument(0);
            persisted.setStepCount(progress.getStepCount()); persisted.setPromptTokens(progress.getPromptTokens());
            persisted.setCompletionTokens(progress.getCompletionTokens()); persisted.setTotalTokens(progress.getTotalTokens());
            return null;
        }).when(states).save(any());
        when(runtime.execute(any())).thenAnswer(call -> {
            AgentRuntimePort.Request request = call.getArgument(0);
            assertEquals(199_800, request.remainingTokens());
            assertEquals(6, request.maxSteps());
            request.usageObserver().accept(new AgentRuntimePort.Usage(1, 10, 5));
            throw new BusinessException(502, "后续模型调用失败");
        });
        var prepared = new AgentAskService.Prepared("run-1", 10L, 20L, "session", "question", 7L, null,
                new AgentExecutionPreparation.ModelBinding(new ModelConfig(), cn.sdh.backend.service.factory.ModelProvider.OPENAI, null),
                "system", false, null, null, null, null, null, List.of(), 8, true, null, null);
        var events = service.execute(prepared).collectList().block();
        assertNotNull(events);
        assertTrue(events.stream().anyMatch(event -> event.contains("后续模型调用失败")));
        assertEquals("FAILED", persisted.getStatus());
        assertEquals(3, persisted.getStepCount());
        assertEquals(110, persisted.getPromptTokens());
        assertEquals(55, persisted.getCompletionTokens());
        assertEquals(215, persisted.getTotalTokens());
        verify(states).save(any());
        verify(actions, never()).request(any(), anyLong());
    }
}
