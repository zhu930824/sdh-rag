package cn.sdh.backend.workflow;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.Workflow;
import cn.sdh.backend.entity.WorkflowExecution;
import cn.sdh.backend.service.KnowledgeAccessService;
import cn.sdh.backend.service.WorkflowExecutionService;
import cn.sdh.backend.service.WorkflowService;
import cn.sdh.backend.service.AgentActionService;
import cn.sdh.backend.entity.AgentAction;
import java.util.List;
import cn.sdh.backend.workflow.executor.NodeExecutorFactory;
import cn.sdh.backend.workflow.executor.WorkflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineAuthorizationTest {
    @Mock KnowledgeAccessService access;
    @Mock NodeExecutorFactory executors;
    @Mock WorkflowExecutionService workflowExecutions;
    @Mock WorkflowService workflows;
    @Mock AgentActionService actions;
    WorkflowEngine engine;

    @BeforeEach void setup() {
        engine = new WorkflowEngine();
        ReflectionTestUtils.setField(engine, "knowledgeAccess", access);
        ReflectionTestUtils.setField(engine, "executorFactory", executors);
        ReflectionTestUtils.setField(engine, "workflowExecutionService", workflowExecutions);
        ReflectionTestUtils.setField(engine, "workflowService", workflows);
        ReflectionTestUtils.setField(engine, "agentActions", actions);
        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(9L);
        execution.setWorkflowId(3L);
        lenient().when(workflowExecutions.create(anyLong(), anyLong(), anyString())).thenReturn(execution);
    }

    @Test void onlyWorkflowOwnerCanExecute() {
        Workflow workflow = workflow();
        BusinessException denied = assertThrows(BusinessException.class,
            () -> engine.executeWithCallback(workflow, "", null, 2L));
        assertEquals(403, denied.getCode());
        verifyNoInteractions(access, executors, workflowExecutions);
    }

    @Test void revokedKnowledgeAccessStopsRetrievalBeforeExecutor() {
        Workflow workflow = workflow();
        doThrow(new BusinessException(403, "denied")).when(access).requireRead(7L, 1L);

        var response = engine.executeWithCallback(workflow, "question", null, 1L);

        assertEquals("FAILED", response.getStatus());
        verify(access).requireRead(7L, 1L);
        verifyNoInteractions(executors);
    }

    private Workflow workflow() {
        Workflow workflow = new Workflow();
        workflow.setId(3L);
        workflow.setUserId(1L);
        workflow.setFlowData("{\"nodes\":[{\"id\":\"r1\",\"type\":\"retrieval\",\"data\":{\"label\":\"retrieve\",\"knowledgeBaseId\":7}}],\"edges\":[]}");
        return workflow;
    }

    @Test void manualResumeCannotBypassUncertainOrFailedActions() {
        WorkflowExecution execution = new WorkflowExecution(); execution.setId(9L); execution.setWorkflowId(3L);
        execution.setStatus("WAITING_APPROVAL");
        when(workflowExecutions.getById(9L)).thenReturn(execution);
        when(workflows.getById(3L)).thenReturn(workflow());
        for (String status : List.of("UNKNOWN", "READY", "FAILED", "REJECTED")) {
            AgentAction action = new AgentAction(); action.setStatus(status);
            when(actions.listByWorkflowExecution(9L)).thenReturn(List.of(action));
            BusinessException error = assertThrows(BusinessException.class, () -> engine.resume(9L, 1L, null));
            assertEquals(409, error.getCode());
        }
        verify(workflowExecutions, never()).transition(anyLong(), anyString(), anyString());
        verifyNoInteractions(executors);
    }
}
