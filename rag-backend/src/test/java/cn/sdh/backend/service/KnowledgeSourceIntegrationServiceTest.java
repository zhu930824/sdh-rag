package cn.sdh.backend.service;

import cn.sdh.backend.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeSourceIntegrationServiceTest {
    @Mock KnowledgeSourceService sources;
    @Mock KnowledgeSourceApiKeyService apiKeys;
    KnowledgeSourceIntegrationService service;
    KnowledgeSourceApiKeyService.AuthenticatedApiKey authentication;

    @BeforeEach void setup() {
        service = new KnowledgeSourceIntegrationService(sources, apiKeys);
        authentication = new KnowledgeSourceApiKeyService.AuthenticatedApiKey(2L, 3L, 7L, 8L, "ERP", 60);
    }

    @Test void successfulPushRecordsTaskInAudit() {
        var command = new KnowledgeSourceService.PushCommand("cursor", List.of(
                new KnowledgeSourceService.PushItem("1", "标题", "内容", null, null, null, false)));
        when(sources.ingest(7L, 3L, command))
                .thenReturn(new KnowledgeSourceService.IngestResult(99L, 1, 1, 0, 0, 0));

        var result = service.ingest(authentication, 3L, command, "request-1", "agent");

        assertEquals(99L, result.taskId());
        verify(apiKeys).recordAudit(eq(authentication), eq("request-1"), eq(1), eq(99L), eq("SUCCESS"),
                eq(200), isNull(), anyLong(), eq("agent"));
    }

    @Test void wrongSourceIsRejectedAndAudited() {
        var command = new KnowledgeSourceService.PushCommand(null, List.of(
                new KnowledgeSourceService.PushItem("1", "标题", "内容", null, null, null, false)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.ingest(authentication, 4L, command, "request-2", null));

        assertEquals(403, error.getCode());
        verifyNoInteractions(sources);
        verify(apiKeys).recordAudit(eq(authentication), eq("request-2"), eq(1), isNull(), eq("FAILED"),
                eq(403), contains("无权"), anyLong(), isNull());
    }

    @Test void ingestionFailureKeepsBusinessStatusInAudit() {
        var command = new KnowledgeSourceService.PushCommand(null, List.of(
                new KnowledgeSourceService.PushItem("1", "标题", "内容", null, null, null, false)));
        when(sources.ingest(7L, 3L, command)).thenThrow(new BusinessException(400, "内容不合法"));

        assertThrows(BusinessException.class,
                () -> service.ingest(authentication, 3L, command, "request-3", "agent"));
        verify(apiKeys).recordAudit(eq(authentication), eq("request-3"), eq(1), isNull(), eq("FAILED"),
                eq(400), eq("内容不合法"), anyLong(), eq("agent"));
    }
}
