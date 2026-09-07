package cn.sdh.backend.service;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.*;
import cn.sdh.backend.knowledge.connector.KnowledgeSourceConnector;
import cn.sdh.backend.knowledge.connector.KnowledgeSourceConnectorRegistry;
import cn.sdh.backend.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeSourceServiceTest {
    @Mock KnowledgeSourceMapper sources;
    @Mock KnowledgeSourceItemMapper items;
    @Mock KnowledgeSyncTaskMapper tasks;
    @Mock KnowledgeDocumentMapper documents;
    @Mock KnowledgeDocumentRelationMapper relations;
    @Mock KnowledgeBaseMapper bases;
    @Mock KnowledgeAccessService access;
    @Mock MinioService minio;
    @Mock DocumentProcessService processor;
    @Mock KnowledgeSourceConnector connector;
    @Mock KnowledgeSourceCredentialResolver credentialResolver;
    KnowledgeSourceService service;

    @BeforeEach void setup() {
        UserContext context = new UserContext();
        context.setUserId(8L);
        UserContext.setUserContext(context);
        when(connector.type()).thenReturn("api_push");
        service = new KnowledgeSourceService(sources, items, tasks, documents, relations, bases, access,
                new KnowledgeSourceConnectorRegistry(List.of(connector)), minio, processor, new ObjectMapper(), credentialResolver);
    }

    @AfterEach void clear() { UserContext.clear(); }

    @Test void pushCreatesDocumentRelationAndAuditTask() {
        KnowledgeSource source = source();
        when(sources.selectById(3L)).thenReturn(source);
        when(connector.mode()).thenReturn(KnowledgeSourceConnector.Mode.PUSH);
        doAnswer(invocation -> { ((KnowledgeSyncTask) invocation.getArgument(0)).setId(99L); return 1; }).when(tasks).insert(any(KnowledgeSyncTask.class));
        doAnswer(invocation -> { ((KnowledgeDocument) invocation.getArgument(0)).setId(10L); return 1; }).when(documents).insert(any(KnowledgeDocument.class));
        KnowledgeBase base = new KnowledgeBase();
        base.setId(7L); base.setChunkSize(500); base.setChunkOverlap(50); base.setEmbeddingModel("embed");
        when(bases.selectById(7L)).thenReturn(base);
        when(minio.uploadFile(any(), anyString(), anyString(), anyLong())).thenAnswer(i -> i.getArgument(1));

        var result = service.ingest(7L, 3L, new KnowledgeSourceService.PushCommand("c1", List.of(
                new KnowledgeSourceService.PushItem("ticket-1", "退款规则", "退款需要在七天内申请", null, null,
                        Map.of("department", "客服部", "docType", "policy"), false))));

        assertEquals(1, result.created());
        assertEquals(99L, result.taskId());
        verify(relations).insert(any(KnowledgeDocumentRelation.class));
        verify(processor).processDocumentForKnowledge(10L, 7L, 500, 50);
        verify(items).insert(argThat((KnowledgeSourceItem item) -> "ticket-1".equals(item.getExternalId()) && item.getVersionNo() == 1));
        assertEquals("c1", source.getSyncCursor());
    }

    @Test void unchangedContentIsIdempotent() {
        KnowledgeSource source = source();
        when(sources.selectById(3L)).thenReturn(source);
        when(connector.mode()).thenReturn(KnowledgeSourceConnector.Mode.PUSH);
        doAnswer(invocation -> { ((KnowledgeSyncTask) invocation.getArgument(0)).setId(100L); return 1; }).when(tasks).insert(any(KnowledgeSyncTask.class));
        KnowledgeSourceItem mapping = new KnowledgeSourceItem();
        mapping.setId(5L); mapping.setSourceId(3L); mapping.setExternalId("ticket-1"); mapping.setItemStatus("ACTIVE");
        mapping.setContentHash(DigestUtils.md5DigestAsHex("same".getBytes(StandardCharsets.UTF_8)));
        when(items.selectOne(any())).thenReturn(mapping);

        var result = service.ingest(7L, 3L, new KnowledgeSourceService.PushCommand("c2", List.of(
                new KnowledgeSourceService.PushItem("ticket-1", "title", "same", null, null, Map.of(), false))));

        assertEquals(1, result.unchanged());
        verifyNoInteractions(minio, documents, relations, processor);
        verify(items).updateById(mapping);
    }

    @Test void ordinaryConfigurationRejectsEmbeddedSecrets() {
        var command = new KnowledgeSourceService.SourceCommand("source", "api_push",
                Map.of("headers", Map.of("Authorization", "Bearer secret")), null, 1);
        assertThrows(BusinessException.class, () -> service.create(7L, command));
        verify(sources, never()).insert(any(KnowledgeSource.class));
    }

    @Test void scheduledPullAdvancesCursorAndResetsFailures() {
        KnowledgeSource source = source();
        source.setSyncEnabled(1); source.setSyncIntervalMinutes(60); source.setConsecutiveFailures(2);
        source.setConfigJson("{}");
        when(sources.selectById(3L)).thenReturn(source);
        when(connector.mode()).thenReturn(KnowledgeSourceConnector.Mode.PULL);
        when(connector.fetch(any())).thenReturn(new KnowledgeSourceConnector.FetchResult(List.of(), "next-1", false));
        doAnswer(invocation -> { ((KnowledgeSyncTask) invocation.getArgument(0)).setId(101L); return 1; })
                .when(tasks).insert(any(KnowledgeSyncTask.class));

        var result = service.pullScheduled(3L);

        assertEquals(0, result.scanned());
        assertEquals("next-1", source.getSyncCursor());
        assertEquals("COMPLETED", source.getLastSyncStatus());
        assertEquals(0, source.getConsecutiveFailures());
        assertNotNull(source.getNextSyncTime());
        verify(tasks).updateById(argThat((KnowledgeSyncTask task) -> "SCHEDULED_PULL".equals(task.getTriggerType())
                && "COMPLETED".equals(task.getTaskStatus())));
    }

    @Test void failedPullSchedulesExponentialRetryWithoutAdvancingStoredCursor() {
        KnowledgeSource source = source();
        source.setSyncEnabled(1); source.setSyncIntervalMinutes(60); source.setConsecutiveFailures(0);
        source.setSyncCursor("stable"); source.setConfigJson("{}");
        when(sources.selectById(3L)).thenReturn(source);
        when(connector.mode()).thenReturn(KnowledgeSourceConnector.Mode.PULL);
        when(connector.fetch(any())).thenThrow(new BusinessException(502, "upstream unavailable"));
        doAnswer(invocation -> { ((KnowledgeSyncTask) invocation.getArgument(0)).setId(102L); return 1; })
                .when(tasks).insert(any(KnowledgeSyncTask.class));

        assertThrows(BusinessException.class, () -> service.pullScheduled(3L));

        assertEquals("stable", source.getSyncCursor());
        assertEquals("FAILED", source.getLastSyncStatus());
        assertEquals(1, source.getConsecutiveFailures());
        assertNotNull(source.getNextSyncTime());
        verify(tasks).updateById(argThat((KnowledgeSyncTask task) -> "FAILED".equals(task.getTaskStatus())));
    }

    private KnowledgeSource source() {
        KnowledgeSource source = new KnowledgeSource();
        source.setId(3L); source.setKnowledgeId(7L); source.setSourceType("api_push"); source.setStatus(1); source.setCreatedBy(8L);
        return source;
    }
}
