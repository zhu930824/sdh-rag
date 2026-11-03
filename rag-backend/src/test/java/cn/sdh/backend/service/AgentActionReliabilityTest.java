package cn.sdh.backend.service;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.demo.InternalToolDispatcher;
import cn.sdh.backend.agent.RunExecutionLeaseService;
import cn.sdh.backend.entity.*;
import cn.sdh.backend.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentActionReliabilityTest {
    private final AgentActionMapper mapper = mock(AgentActionMapper.class);
    private final AgentToolService tools = mock(AgentToolService.class);
    private final AgentToolHttpClient http = mock(AgentToolHttpClient.class);
    private final AgentActionJobs actionJobs = mock(AgentActionJobs.class);
    private final AgentActionCompletionService completion = new AgentActionCompletionService(mapper, actionJobs, mock(AgentStepMapper.class));
    private final AgentActionExecutor executor = new AgentActionExecutor(mapper, tools, http,
            mock(InternalToolDispatcher.class), new ObjectMapper(), completion);
    private final AgentActionService service = new AgentActionService(mapper, mock(AgentActionFeedbackMapper.class),
            tools, executor, actionJobs, completion, new ObjectMapper(), mock(AgentImprovementService.class), transactions());

    @Test void uncertainOrAlreadyExecutingActionsCannotBeDispatchedAgain() {
        for (String status : java.util.List.of("EXECUTING", "UNKNOWN", "SUCCEEDED")) {
            AgentAction existing = new AgentAction(); existing.setId(8L); existing.setExecutionVersion(1); existing.setStatus(status);
            when(mapper.selectById(8L)).thenReturn(existing);
            assertEquals(status, executor.execute(8L, 1).getStatus());
        }
        verifyNoInteractions(http);
        verify(mapper, never()).claimReady(anyLong(), anyInt());
    }

    @Test void acceptingAWriteNeverCallsTheBusinessSystemOnTheRequestThread() {
        when(tools.requireUsable(1L, 7L)).thenReturn(tool());
        AtomicReference<AgentAction> saved = new AtomicReference<>();
        when(mapper.insert(any(AgentAction.class))).thenAnswer(call -> {
            AgentAction action = call.getArgument(0); action.setId(8L); saved.set(action); return 1;
        });
        when(mapper.selectById(8L)).thenAnswer(call -> saved.get());
        AgentAction result = service.request(command(Map.of("orderId", "42")), 7L);
        assertEquals("READY", result.getStatus());
        verifyNoInteractions(http);
        verify(actionJobs).enqueueExecution(result);
    }

    @Test void writeTimeoutIsUnknownRatherThanSafelyRetryableFailure() {
        AgentTool tool = tool();
        when(tools.requireUsable(1L, 7L)).thenReturn(tool);
        AtomicReference<AgentAction> saved = new AtomicReference<>();
        when(mapper.insert(any(AgentAction.class))).thenAnswer(call -> {
            AgentAction action = call.getArgument(0); action.setId(8L); saved.set(action); return 1;
        });
        when(mapper.selectById(8L)).thenAnswer(call -> saved.get());
        when(mapper.claimReady(8L, 1)).thenReturn(1);
        when(mapper.finishExecuting(any())).thenReturn(1);
        when(http.execute(eq(tool), anyMap())).thenThrow(new BusinessException(502, "timeout"));
        service.request(command(Map.of("orderId", "42")), 7L);
        AgentAction result = executor.execute(8L, 1);
        assertEquals("UNKNOWN", result.getStatus());
        verify(http, times(1)).execute(eq(tool), anyMap());
    }

    @Test void sameIdempotencyKeyWithDifferentParametersMustConflict() {
        when(tools.requireUsable(1L, 7L)).thenReturn(tool());
        AgentAction existing = new AgentAction(); existing.setRequestJson("{\"orderId\":\"42\"}");
        when(mapper.selectOne(any())).thenReturn(existing);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.request(command(Map.of("orderId", "99")), 7L));
        assertEquals(409, error.getCode());
        verifyNoInteractions(http);
    }

    private RunExecutionLeaseService transactions() {
        var leases = mock(RunExecutionLeaseService.class);
        when(leases.transaction(any())).thenAnswer(call -> ((java.util.function.Supplier<?>) call.getArgument(0)).get());
        return leases;
    }

    private AgentTool tool() {
        AgentTool tool = new AgentTool(); tool.setId(1L); tool.setName("创建工单");
        tool.setEndpoint("https://business.example/tickets"); tool.setHttpMethod("POST");
        tool.setRiskLevel("LOW"); tool.setApprovalMode("NEVER"); return tool;
    }
    private AgentActionService.ActionCommand command(Map<String, Object> args) {
        return new AgentActionService.ActionCommand(1L, args, null, "operation-1", null, null, null, null, null);
    }
}
