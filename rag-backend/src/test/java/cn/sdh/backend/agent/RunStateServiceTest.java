package cn.sdh.backend.agent;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.AgentRun;
import cn.sdh.backend.mapper.AgentRunMapper;
import cn.sdh.backend.mapper.AgentStepMapper;
import cn.sdh.backend.mapper.ChatHistoryMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunStateServiceTest {
    private final AgentRunMapper runs = mock(AgentRunMapper.class);
    private final RunStateService service = new RunStateService(runs, mock(AgentStepMapper.class), mock(ChatHistoryMapper.class), mock(RunExecutionLeaseService.class));

    @Test void oversizedHistoryRetainsCompleteRecentMessages() {
        String recent = "最新消息\n\"引用\"";
        service.persistRounds(1L, List.of("旧".repeat(300_000), "中".repeat(210_000), recent));
        ArgumentCaptor<AgentRun> saved = ArgumentCaptor.forClass(AgentRun.class);
        verify(runs).updateById(saved.capture());
        String snapshot = saved.getValue().getMessagesJson();
        assertTrue(snapshot.length() <= 500_000);
        assertEquals(List.of("中".repeat(210_000), recent), service.loadRounds(snapshot));
    }

    @Test void oversizedSingleMessageFailsBeforeOverwritingCheckpoint() {
        assertThrows(BusinessException.class,
                () -> service.persistRounds(1L, List.of("x".repeat(500_000))));
        verifyNoInteractions(runs);
    }

    @Test void corruptedOrWrongShapeCheckpointMustNotBecomeEmptyConversation() {
        for (String json : List.of("broken]", "null", "[{}]", "[null]")) {
            BusinessException error = assertThrows(BusinessException.class, () -> service.loadRounds(json));
            assertEquals(409, error.getCode());
        }
    }

    @Test void absentAndValidLegacySnapshotsAreSupported() {
        assertEquals(List.of(), service.loadRounds(null));
        assertEquals(List.of(), service.loadRounds("[]"));
        assertEquals(List.of("回答"), service.loadRounds("[\"回答\"]"));
    }
}
