package cn.sdh.backend.service;

import cn.sdh.backend.entity.AgentAction;
import cn.sdh.backend.entity.AgentActionFeedback;
import cn.sdh.backend.entity.AgentImprovementItem;
import cn.sdh.backend.entity.AgentRun;
import cn.sdh.backend.mapper.AgentImprovementItemMapper;
import cn.sdh.backend.mapper.AgentRunMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentImprovementServiceTest {
    @Mock AgentImprovementItemMapper items;
    @Mock AgentRunMapper runs;

    @Test void reusableBusinessFeedbackCreatesKnowledgeImprovementItem() {
        AgentImprovementService service = new AgentImprovementService(items, runs);
        AgentAction action = new AgentAction();
        action.setId(11L);
        action.setRunId(22L);
        action.setToolName("创建售后工单");
        action.setRequestJson("{\"orderNo\":\"A1\"}");
        action.setResultJson("{\"ticketNo\":\"T1\"}");
        AgentActionFeedback feedback = new AgentActionFeedback();
        feedback.setId(33L);
        feedback.setUserId(7L);
        feedback.setOutcome("PARTIAL");
        feedback.setComment("需要补充退换货时效");
        feedback.setReusableKnowledge(1);
        AgentRun run = new AgentRun();
        run.setId(22L);
        run.setKnowledgeId(9L);
        when(runs.selectById(22L)).thenReturn(run);

        service.captureFeedback(action, feedback);

        ArgumentCaptor<AgentImprovementItem> captor = ArgumentCaptor.forClass(AgentImprovementItem.class);
        verify(items).insert(captor.capture());
        assertEquals(9L, captor.getValue().getKnowledgeId());
        assertEquals("KNOWLEDGE_REUSE", captor.getValue().getCategory());
        assertEquals("MEDIUM", captor.getValue().getPriority());
    }

    @Test void feedbackNotMarkedReusableDoesNotCreateQueueItem() {
        AgentImprovementService service = new AgentImprovementService(items, runs);
        AgentActionFeedback feedback = new AgentActionFeedback();
        feedback.setReusableKnowledge(0);
        service.captureFeedback(new AgentAction(), feedback);
        verifyNoInteractions(items, runs);
    }
}
