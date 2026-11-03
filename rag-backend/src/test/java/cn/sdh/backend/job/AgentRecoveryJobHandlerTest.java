package cn.sdh.backend.job;

import cn.sdh.backend.agent.*;
import cn.sdh.backend.entity.*;
import cn.sdh.backend.service.PlatformJobService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AgentRecoveryJobHandlerTest {
    private final AgentRunRecoveryService recovery = mock(AgentRunRecoveryService.class);
    private final AgentAskService agents = mock(AgentAskService.class);
    private final RunStateService runs = mock(RunStateService.class);
    private final PlatformJobService jobs = mock(PlatformJobService.class);
    private final AgentRecoveryJobHandler handler = new AgentRecoveryJobHandler(recovery, agents, runs);

    @Test void duplicateOrStaleRecoveryNeverCallsTheModel() {
        PlatformJob job = job();
        assertTrue(handler.execute(job, context(job)).contains("跳过"));
        verifyNoInteractions(agents);
    }

    @Test void failedPreparationReturnsTheRunToAnExplicitlyRecoverableBoundary() {
        PlatformJob job = job();
        when(recovery.begin(any(), eq(job))).thenReturn(true);
        when(agents.restartRecovered(eq("run-1"), eq(7L), any())).thenThrow(new IllegalStateException("model unavailable"));
        assertThrows(IllegalStateException.class, () -> handler.execute(job, context(job)));
        verify(recovery).failBeforeClaim(new AgentRunRecoveryService.Payload("run-1", 1), job.getId());
    }

    @Test void recoveryUsesTheExistingRuntimeAndProvidesAJobOwnershipCheckpoint() {
        PlatformJob job = job();
        when(recovery.begin(any(), eq(job))).thenReturn(true);
        when(agents.restartRecovered(eq("run-1"), eq(7L), any())).thenAnswer(call -> {
            ((Runnable) call.getArgument(2)).run(); return Flux.just("done");
        });
        AgentRun result = new AgentRun(); result.setStatus(AgentRun.STATUS_COMPLETED);
        when(runs.getByRunKey("run-1")).thenReturn(result);
        assertTrue(handler.execute(job, context(job)).contains("COMPLETED"));
        verify(jobs, atLeast(2)).heartbeat(1L, "worker");
    }

    private PlatformJob job() {
        PlatformJob job = new PlatformJob(); job.setId(1L); job.setCreatedBy(7L);
        job.setStatus(PlatformJob.RUNNING); job.setLeaseOwner("worker");
        job.setPayloadJson("{\"runKey\":\"run-1\",\"generation\":1}"); return job;
    }

    @Test void aCancelledRunMakesTheOwningJobCancelRatherThanFailOrSucceed() {
        PlatformJob job = job();
        when(recovery.begin(any(), eq(job))).thenReturn(true);
        when(agents.restartRecovered(eq("run-1"), eq(7L), any())).thenReturn(Flux.empty());
        AgentRun result = new AgentRun(); result.setStatus(AgentRun.STATUS_CANCELLED);
        when(runs.getByRunKey("run-1")).thenReturn(result);
        assertThrows(PlatformJobCancelledException.class, () -> handler.execute(job, context(job)));
    }
    private PlatformJobExecutionContext context(PlatformJob job) {
        when(jobs.get(1L)).thenReturn(job); when(jobs.heartbeat(1L, "worker")).thenReturn(true);
        return new PlatformJobExecutionContext(1L, "worker", jobs);
    }
}
