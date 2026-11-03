package cn.sdh.backend.agent;

import cn.sdh.backend.agent.runtime.*;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.*;
import cn.sdh.backend.service.*;
import cn.sdh.backend.service.factory.ModelProvider;
import cn.sdh.backend.service.impl.ChatServiceImpl;
import cn.sdh.backend.job.PlatformJobCancelledException;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.BeanUtils;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AgentAskServiceLifecycleTest {
    @Test void bothEnginesPublishBeforeMemoryAndEmitOneDoneEvent() {
        for (String engine : List.of("legacy", "agentscope")) {
            var f = new Fixture(engine);
            doAnswer(call -> { assertTrue(f.committed.get()); return null; }).when(f.chat).storeMemoryAfterTurn(anyLong(), anyString(), anyString(), anyString(), eq(true));
            var events = f.execute(true);
            assertEquals("COMPLETED", f.row.get().getStatus());
            assertEquals("answer", f.answer.get());
            assertEquals(1, events.stream().filter(e -> e.contains("\"type\":\"done\"")).count());
            assertNotNull(f.row.get().getCompletedAt());
        }
    }
    @Test void sdkSuspendsAtTheSamePublicationBoundaryForApprovalAndQueuedActions() {
        for (String status : List.of("PENDING_APPROVAL", "READY")) {
            var f = new Fixture("agentscope");
            AgentTool tool = new AgentTool(); tool.setId(1L); tool.setCode("ticket"); tool.setName("工单");
            AgentAction action = new AgentAction(); action.setId(30L); action.setStatus(status); action.setApproverId(7L);
            when(f.actions.request(any(), eq(7L))).thenReturn(action);
            doAnswer(call -> {
                AgentRuntimePort.Request request = call.getArgument(0);
                assertTrue(request.executor().execute(tool, "call-1", Map.of()).suspended());
                return new AgentRuntimePort.Result(AgentRuntimePort.Status.SUSPENDED, "", 1, 4, 2);
            }).when(f.runtime).execute(any());
            var events = f.execute(false);
            assertEquals("PENDING_APPROVAL".equals(status) ? "WAITING_APPROVAL" : "WAITING_ACTION", f.row.get().getStatus());
            assertEquals(30L, f.row.get().getPendingActionId());
            assertTrue(f.answer.get().contains("暂停"));
            assertFalse(events.stream().anyMatch(e -> e.contains("\"type\":\"done\"")));
            assertEquals("PENDING_APPROVAL".equals(status), events.stream().anyMatch(e -> e.contains("approval_required")));
        }
    }
    @Test void failedAndCancelledExecutionsKeepTheirDistinctTerminalStates() {
        for (RuntimeException failure : List.of(new IllegalStateException("model failed"), new PlatformJobCancelledException())) {
            var f = new Fixture("agentscope");
            doThrow(failure).when(f.runtime).execute(any());
            var events = f.execute(false);
            assertEquals(failure instanceof PlatformJobCancelledException ? "CANCELLED" : "FAILED", f.row.get().getStatus());
            assertTrue(events.stream().anyMatch(e -> e.contains("\"type\":\"error\"")));
            assertFalse(events.stream().anyMatch(e -> e.contains("\"type\":\"done\"")));
        }
    }
    @Test void aLosingSubscriberNeverPublishesOrCallsTheModel() {
        var f = new Fixture("agentscope");
        when(f.leases.claim(eq("run-1"), eq(7L), any())).thenThrow(new BusinessException(409, "busy"));
        var events = f.execute(false);
        assertEquals("RUNNING", f.row.get().getStatus());
        assertFalse(f.committed.get());
        verifyNoInteractions(f.runtime);
        assertFalse(events.stream().anyMatch(e -> e.contains("agent_status")));
    }
    @Test void aLostFenceCannotPublishDoneOrStoreMemory() {
        var f = new Fixture("agentscope");
        when(f.states.publish(any(RunExecutionLeaseService.Fence.class), any(), anyString(), anyString(), any(), any(), any()))
                .thenThrow(new BusinessException(409, "lost fence"));
        var events = f.execute(true);
        assertEquals("RUNNING", f.row.get().getStatus());
        verify(f.chat, never()).storeMemoryAfterTurn(anyLong(), anyString(), anyString(), anyString(), anyBoolean());
        assertFalse(events.stream().anyMatch(e -> e.contains("\"type\":\"done\"") || e.contains("agent_status")));
    }
    @Test void oversizedTraceIsStillValidJsonWithCompleteSteps() {
        var f = new Fixture("agentscope");
        List<AgentStep> steps = new ArrayList<>();
        for (int i=0; i<50; i++) {
            AgentStep step = new AgentStep(); step.setSeq(i+1); step.setStepType("tool"); step.setStatus("SUCCEEDED");
            step.setInputJson("参".repeat(3000)); step.setOutputJson("果".repeat(7000)); steps.add(step);
        }
        when(f.states.stepsOf(10L)).thenReturn(steps);
        f.execute(false);
        assertTrue(f.trace.get().length() <= 50 * 1024);
        assertTrue(JSON.isValidObject(f.trace.get()), "A capped trace must remain a complete JSON object");
        var trace = JSON.parseObject(f.trace.get());
        assertFalse(trace.getJSONArray("steps").isEmpty());
        assertTrue(trace.getJSONArray("steps").size() <= 50);
    }
    @Test void aMemoryFailureAfterCompletionDoesNotTurnACommittedAnswerIntoAStreamError() {
        var f = new Fixture("agentscope");
        doThrow(new IllegalStateException("memory unavailable")).when(f.chat).storeMemoryAfterTurn(anyLong(), anyString(), anyString(), anyString(), anyBoolean());
        var events = f.execute(true);
        assertEquals("COMPLETED", f.row.get().getStatus());
        assertTrue(events.stream().anyMatch(e -> e.contains("\"type\":\"done\"")));
        assertFalse(events.stream().anyMatch(e -> e.contains("\"type\":\"error\"")));
    }

    @Test void rejectingAWaitingRunPublishesOnceWithoutCallingEitherModel() {
        var f = new Fixture("agentscope"); f.row.get().setStatus("WAITING_ACTION");
        AgentAction rejected = new AgentAction(); rejected.setRunId(10L); rejected.setStatus("REJECTED");
        rejected.setToolName("工单"); rejected.setErrorMessage("审批人拒绝");
        f.service.finalizeRejected(rejected);
        assertEquals("REJECTED", f.row.get().getStatus());
        assertTrue(f.answer.get().contains("已被拒绝"));
        f.service.finalizeRejected(rejected);
        verify(f.states, times(1)).publish(any(AgentRun.class), eq("WAITING_ACTION"), anyString(), any(), any(), any());
        verifyNoInteractions(f.runtime);
        verify(f.model, never()).call(any(Prompt.class));
    }

    private static final class Fixture {
        final RunStateService states = mock(RunStateService.class);
        final RunExecutionLeaseService leases = mock(RunExecutionLeaseService.class);
        final AgentScopeRuntimeAdapter runtime = mock(AgentScopeRuntimeAdapter.class);
        final AgentActionService actions = mock(AgentActionService.class);
        final ChatServiceImpl chat = mock(ChatServiceImpl.class);
        final ChatModel model = mock(ChatModel.class);
        final AtomicReference<AgentRun> row = new AtomicReference<>();
        final AtomicReference<String> answer = new AtomicReference<>(), trace = new AtomicReference<>();
        final AtomicBoolean committed = new AtomicBoolean();
        final AgentAskService service;
        Fixture(String engine) {
            AgentRun run = new AgentRun(); run.setId(10L); run.setRunKey("run-1"); run.setUserId(7L); run.setHistoryId(20L);
            run.setStatus("RUNNING"); run.setEngine(engine); run.setMaxSteps(8); run.setStepCount(0);
            run.setPromptTokens(0); run.setCompletionTokens(0); run.setTotalTokens(0); row.set(run);
            var lease = mock(RunExecutionLeaseService.Lease.class);
            when(leases.claim(eq("run-1"), eq(7L), any())).thenReturn(lease);
            when(lease.fence()).thenReturn(new RunExecutionLeaseService.Fence(10, "run-1", 7L, "owner", 1));
            when(states.getByRunKey("run-1")).thenAnswer(call -> copy(row.get()));
            when(states.getById(10L)).thenAnswer(call -> copy(row.get()));
            when(states.guarded(any(), any())).thenAnswer(call -> ((Supplier<?>)call.getArgument(1)).get());
            when(states.publish(any(RunExecutionLeaseService.Fence.class), any(), anyString(), anyString(), any(), any(), any()))
                    .thenAnswer(call -> { row.set(copy(call.getArgument(1))); answer.set(call.getArgument(3)); trace.set(call.getArgument(6)); committed.set(true); return true; });
            when(states.publish(any(AgentRun.class), anyString(), anyString(), any(), any(), any()))
                    .thenAnswer(call -> { row.set(copy(call.getArgument(0))); answer.set(call.getArgument(2)); trace.set(call.getArgument(5)); committed.set(true); return true; });
            when(states.appendStep(any(RunExecutionLeaseService.Fence.class), anyLong(), anyString(), any(), any(), any(), any(), anyString(), any(), any()))
                    .thenAnswer(call -> { AgentStep step = new AgentStep(); step.setId(1L); step.setRunId(10L); return step; });
            when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("answer")))));
            when(runtime.execute(any())).thenAnswer(call -> {
                AgentRuntimePort.Request request = call.getArgument(0); request.textDelta().accept("answer");
                return new AgentRuntimePort.Result(AgentRuntimePort.Status.COMPLETED, "answer", 1, 4, 2);
            });
            service = new AgentAskService(chat, actions, null, states, mock(ModelConfigService.class), null, null, runtime, null, leases, null, new AgentRunLifecycle(states));
        }
        List<String> execute(boolean memory) {
            return service.execute(new AgentAskService.Prepared("run-1",10L,20L,"session","question",7L,null,1L,
                    model,ModelProvider.OPENAI,"system",memory,null,null,null,null,null,List.of(),8,false,null,null)).collectList().block();
        }
        static AgentRun copy(AgentRun original) { var copy = new AgentRun(); BeanUtils.copyProperties(original, copy); return copy; }
    }
}
