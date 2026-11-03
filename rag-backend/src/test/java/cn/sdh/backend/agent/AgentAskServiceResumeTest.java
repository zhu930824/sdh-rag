package cn.sdh.backend.agent;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.AgentAction;
import cn.sdh.backend.entity.AgentRun;
import cn.sdh.backend.service.AgentActionService;
import cn.sdh.backend.service.PlatformJobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AgentAskServiceResumeTest {
    @Mock AgentActionService actions;
    @Mock RunStateService runs;

    @Test void backgroundResumeIsPersistedInsteadOfStartingAnInProcessSubscription() {
        PlatformJobService jobs = mock(PlatformJobService.class);
        AgentAskService service = new AgentAskService(null, actions, null, runs,
                null, null, null, null, jobs, null, null, null);
        AgentRun run = new AgentRun(); run.setId(10L); run.setRunKey("run-1");
        run.setUserId(7L); run.setStatus(AgentRun.STATUS_WAITING_APPROVAL); run.setPendingActionId(20L);
        when(runs.getByRunKey("run-1")).thenReturn(run);
        AgentAction decided = new AgentAction(); decided.setId(20L); decided.setStatus("SUCCEEDED");
        when(actions.listByRun(10L)).thenReturn(List.of(decided));
        service.resumeInBackground("run-1", 20L);
        verify(jobs).enqueue(argThat(command -> command.jobKey().equals("agent-resume:run-1:20:1")
                && command.jobType().equals("AGENT_RESUME") && command.createdBy().equals(7L)));
    }

    @Test void cannotResumeBeforeActionIsApprovedAndExecuted() {
        AgentAskService service = new AgentAskService(null, actions, null, runs,
                null, null, null, null, null, null, null, null);
        AgentRun run = new AgentRun();
        run.setId(10L);
        run.setRunKey("run-1");
        run.setUserId(7L);
        run.setStatus(AgentRun.STATUS_WAITING_APPROVAL);
        run.setPendingActionId(20L);
        AgentAction pending = new AgentAction();
        pending.setId(20L);
        pending.setRunId(10L);
        pending.setApproverId(7L);
        pending.setStatus("PENDING_APPROVAL");
        when(runs.getByRunKey("run-1")).thenReturn(run);
        when(actions.listByRun(10L)).thenReturn(List.of(pending));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.resumeFlux("run-1", 7L, 20L));

        assertEquals(409, error.getCode());
    }
}
