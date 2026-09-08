package cn.sdh.backend.service;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.entity.KnowledgeDraft;
import cn.sdh.backend.entity.KnowledgePublishRecord;
import cn.sdh.backend.mapper.KnowledgeDocumentMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentRelationMapper;
import cn.sdh.backend.mapper.KnowledgeDraftMapper;
import cn.sdh.backend.mapper.KnowledgePublishRecordMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgePublishingServiceTest {
    @Mock KnowledgeDraftMapper drafts;
    @Mock KnowledgePublishRecordMapper records;
    @Mock KnowledgeDocumentMapper documents;
    @Mock KnowledgeDocumentRelationMapper relations;
    @Mock KnowledgeAccessService access;
    @Mock AgentImprovementService improvements;
    @Mock MinioService minio;
    @Mock DocumentProcessService processor;
    @Mock VectorStoreService vectors;
    KnowledgePublishingService service;

    @BeforeEach void setup() {
        UserContext context = new UserContext();
        context.setUserId(7L);
        UserContext.setUserContext(context);
        service = new KnowledgePublishingService(drafts, records, documents, relations, access,
                improvements, minio, processor, vectors);
    }

    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void createsEditableDraftBoundToAuthorizedKnowledgeBase() {
        when(access.requireEdit(9L, 7L)).thenReturn(base());
        doAnswer(invocation -> {
            KnowledgeDraft value = invocation.getArgument(0);
            value.setId(31L);
            return 1;
        }).when(drafts).insert(any(KnowledgeDraft.class));

        KnowledgeDraft result = service.create(new KnowledgePublishingService.DraftCommand(
                null, 9L, "退款处理经验", "这是经过真实业务验证的退款处理经验，包含适用条件、审核步骤、例外情况以及最终验证结果。正文需要足够完整。",
                List.of("退款超过三十天怎么处理？")));

        assertEquals(31L, result.getId());
        assertEquals("DRAFT", result.getStatus());
        assertEquals("NOT_RUN", result.getValidationStatus());
        assertTrue(result.getRegressionQueries().contains("退款超过三十天"));
    }

    @Test void publishedDraftPassesOnlyWhenEveryRegressionQueryRetrievesDocument() {
        KnowledgeDraft draft = publishedDraft();
        when(drafts.selectById(31L)).thenReturn(draft);
        when(access.requireRead(9L, 7L)).thenReturn(base());
        when(access.requireEdit(9L, 7L)).thenReturn(base());
        Document hit = new Document("命中的业务知识");
        hit.getMetadata().put("document_id", 88L);
        when(vectors.similaritySearch(anyString(), eq(9L), eq(5))).thenReturn(List.of(hit));

        KnowledgePublishingService.ValidationResult result = service.validate(31L);

        assertTrue(result.passed());
        assertEquals(1, result.queries().getFirst().rank());
        assertEquals("PASSED", draft.getValidationStatus());
        verify(records).insert(argThat((KnowledgePublishRecord record) ->
                "VALIDATE".equals(record.getAction()) && "PASSED".equals(record.getStatus())));
    }

    @Test void approvedDraftPublishesThroughExistingDocumentPipeline() {
        KnowledgeDraft draft = publishedDraft();
        draft.setStatus("APPROVED");
        draft.setPublishedDocumentId(null);
        draft.setContent("这是经过人工审核的完整业务知识，包含适用场景、执行步骤、限制条件、异常处理方式和真实业务验证结果，可供后续检索使用。");
        when(drafts.selectById(31L)).thenReturn(draft);
        when(access.requireRead(9L, 7L)).thenReturn(base());
        when(access.requireEdit(9L, 7L)).thenReturn(base());
        when(minio.uploadFile(any(), anyString(), anyString(), anyLong())).thenAnswer(invocation -> invocation.getArgument(1));
        doAnswer(invocation -> {
            KnowledgeDocument value = invocation.getArgument(0);
            value.setId(88L);
            return 1;
        }).when(documents).insert(any(KnowledgeDocument.class));

        KnowledgeDraft result = service.publish(31L);

        assertEquals("PUBLISHING", result.getStatus());
        assertEquals(88L, result.getPublishedDocumentId());
        verify(relations).insert(argThat((cn.sdh.backend.entity.KnowledgeDocumentRelation relation) ->
                relation.getKnowledgeId().equals(9L) && relation.getDocumentId().equals(88L)));
        verify(processor).processDocumentForKnowledge(eq(88L), eq(9L), any());
    }

    @Test void rollbackRemovesSearchLinkButKeepsSourceDocumentForAudit() {
        KnowledgeDraft draft = publishedDraft();
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(88L);
        when(drafts.selectById(31L)).thenReturn(draft);
        when(access.requireRead(9L, 7L)).thenReturn(base());
        when(access.requireEdit(9L, 7L)).thenReturn(base());
        when(relations.selectByKnowledgeIdAndDocumentId(9L, 88L)).thenReturn(new cn.sdh.backend.entity.KnowledgeDocumentRelation());
        when(documents.selectById(88L)).thenReturn(document);

        service.rollback(31L);

        verify(processor).unlinkDocumentFromKnowledge(88L, 9L);
        verify(documents, never()).deleteById(anyLong());
        assertEquals(2, document.getStatus());
        assertEquals("ROLLED_BACK", draft.getStatus());
    }

    @Test void completedIndexIsRemovedWhenAutomaticQualityGateFails() {
        KnowledgeDraft draft = publishedDraft();
        draft.setStatus("PUBLISHING");
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(88L);
        cn.sdh.backend.entity.KnowledgeDocumentRelation relation = new cn.sdh.backend.entity.KnowledgeDocumentRelation();
        relation.setProcessStatus(2);
        relation.setChunkCount(3);
        when(drafts.selectAccessible(7L, null)).thenReturn(List.of(draft));
        when(relations.selectByKnowledgeIdAndDocumentId(9L, 88L)).thenReturn(relation);
        when(documents.selectById(88L)).thenReturn(document);
        when(vectors.similaritySearch(anyString(), eq(9L), eq(5))).thenReturn(List.of());

        service.list(null);

        assertEquals("GATE_FAILED", draft.getStatus());
        assertEquals("FAILED", draft.getValidationStatus());
        assertEquals(2, document.getStatus());
        verify(processor).unlinkDocumentFromKnowledge(88L, 9L);
        verify(records).insert(argThat((KnowledgePublishRecord record) ->
                "VALIDATE".equals(record.getAction()) && "FAILED".equals(record.getStatus())));
    }

    private KnowledgeBase base() {
        KnowledgeBase base = new KnowledgeBase();
        base.setId(9L);
        base.setChunkSize(500);
        base.setChunkOverlap(50);
        base.setEmbeddingModel("test-embedding");
        return base;
    }

    private KnowledgeDraft publishedDraft() {
        KnowledgeDraft draft = new KnowledgeDraft();
        draft.setId(31L);
        draft.setKnowledgeId(9L);
        draft.setCreatedBy(7L);
        draft.setTitle("退款处理经验");
        draft.setContent("完整正文");
        draft.setStatus("PUBLISHED");
        draft.setPublishedDocumentId(88L);
        draft.setRegressionQueries("[\"退款超过三十天怎么处理？\"]");
        return draft;
    }
}
