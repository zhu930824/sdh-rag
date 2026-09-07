package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.dto.AskRequest;
import cn.sdh.backend.dto.MetadataFilter;
import cn.sdh.backend.service.ChatService;
import cn.sdh.backend.service.QaFeedbackService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private QaFeedbackService qaFeedbackService;

    @InjectMocks
    private ChatController controller;

    @BeforeEach
    void setUpUser() {
        UserContext context = new UserContext();
        context.setUserId(42L);
        UserContext.setUserContext(context);
    }

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void shouldForwardMemoryAndMetadataOptionsToChatService() {
        AskRequest request = new AskRequest();
        request.setQuestion("查询制度");
        request.setSessionId("session-1");
        request.setKnowledgeId(9L);
        request.setModelId(3L);
        request.setMemoryEnabled(false);
        request.setMetadataFilter(MetadataFilter.builder()
            .department("财务部")
            .dateFrom(LocalDate.of(2026, 1, 1))
            .build());

        when(chatService.ask(eq("查询制度"), eq("session-1"), eq(42L), eq(9L), eq(3L),
            eq(false), anyMap())).thenReturn(Flux.empty());

        controller.ask(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> filters = ArgumentCaptor.forClass(Map.class);
        verify(chatService).ask(eq("查询制度"), eq("session-1"), eq(42L), eq(9L), eq(3L),
            eq(false), filters.capture());
        assertEquals("财务部", filters.getValue().get("department"));
        assertEquals("2026-01-01", filters.getValue().get("doc_date_gte"));
    }
}
