package cn.sdh.backend.job;

import cn.sdh.backend.entity.*;
import cn.sdh.backend.service.*;
import cn.sdh.backend.workflow.executor.WorkflowEngine;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WorkflowActionResumeJobHandlerTest {
    private final WorkflowExecutionService executions = mock(WorkflowExecutionService.class);
    private final AgentActionService actions = mock(AgentActionService.class);
    private final WorkflowEngine engine = mock(WorkflowEngine.class);
    private final WorkflowActionResumeJobHandler handler = new WorkflowActionResumeJobHandler(executions, actions, engine);

    @Test void unknownResultCannotStartDownstreamNodes() {
        var action = new AgentAction(); action.setStatus("UNKNOWN");
        when(actions.listByWorkflowExecution(9L)).thenReturn(List.of(action));
        handler.execute(job(), mock(PlatformJobExecutionContext.class));
        verifyNoInteractions(engine);
        verify(executions, never()).transition(anyLong(), anyString(), anyString());
    }

    @Test void rejectedActionTerminatesWorkflowWithoutCallingDownstream() {
        var action = new AgentAction(); action.setStatus("REJECTED");
        when(actions.listByWorkflowExecution(9L)).thenReturn(List.of(action));
        when(executions.transition(9L, "WAITING_APPROVAL", "FAILED")).thenReturn(true);
        handler.execute(job(), mock(PlatformJobExecutionContext.class));
        verifyNoInteractions(engine);
        verify(executions).update(argThat(execution -> "FAILED".equals(execution.getStatus())));
    }
    private PlatformJob job() {
        WorkflowExecution execution = new WorkflowExecution(); execution.setId(9L); execution.setUserId(7L);
        execution.setStatus("WAITING_APPROVAL"); when(executions.getById(9L)).thenReturn(execution);
        PlatformJob job = new PlatformJob(); job.setCreatedBy(7L); job.setPayloadJson("{\"executionId\":9}"); return job;
    }
}
