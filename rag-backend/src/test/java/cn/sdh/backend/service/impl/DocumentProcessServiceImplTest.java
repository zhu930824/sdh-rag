package cn.sdh.backend.service.impl;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.dto.DocumentLinkConfig;
import cn.sdh.backend.entity.KnowledgeDocumentRelation;
import cn.sdh.backend.entity.PlatformJob;
import cn.sdh.backend.job.DocumentIndexJobPayload;
import cn.sdh.backend.job.PlatformJobExecutionContext;
import cn.sdh.backend.mapper.KnowledgeBaseMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentRelationMapper;
import cn.sdh.backend.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentProcessServiceImplTest {
    @Mock KnowledgeDocumentMapper documents;
    @Mock KnowledgeDocumentRelationMapper relations;
    @Mock KnowledgeBaseMapper knowledgeBases;
    @Mock MinioService minio;
    @Mock VectorStoreService vectors;
    @Mock DocumentDedupService dedup;
    @Mock SummaryIndexService summaries;
    @Mock PlatformJobService jobs;
    @Mock DocumentIndexLock indexLock;
    @Mock PlatformJobExecutionContext executionContext;

    private DocumentProcessServiceImpl service;

    @BeforeEach
    void setUp() {
        UserContext user = new UserContext();
        user.setUserId(7L);
        UserContext.setUserContext(user);
        service = new DocumentProcessServiceImpl(documents, relations, knowledgeBases, minio,
                vectors, dedup, summaries, jobs, indexLock, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void enqueueAdvancesRevisionAndBindsPersistentJob() {
        KnowledgeDocumentRelation initial = relation(3L, 4L, 5L, 0, null);
        KnowledgeDocumentRelation prepared = relation(3L, 4L, 5L, 1, null);
        when(relations.selectByKnowledgeIdAndDocumentId(5L, 4L)).thenReturn(initial);
        when(relations.updateIndexConfig(eq(3L), any())).thenReturn(1);
        when(relations.prepareNextIndex(3L)).thenReturn(1);
        when(relations.selectById(3L)).thenReturn(prepared);
        PlatformJob persisted = new PlatformJob();
        persisted.setId(9L);
        when(jobs.enqueue(any())).thenReturn(persisted);
        when(relations.attachIndexJob(3L, 1, 9L)).thenReturn(1);

        service.processDocumentForKnowledge(4L, 5L, new DocumentLinkConfig());

        ArgumentCaptor<PlatformJobService.EnqueueCommand> command =
                ArgumentCaptor.forClass(PlatformJobService.EnqueueCommand.class);
        verify(jobs).enqueue(command.capture());
        assertEquals("document-index:3:v1", command.getValue().jobKey());
        assertEquals("DOCUMENT_INDEX", command.getValue().jobType());
        assertEquals(10, command.getValue().maxAttempts());
        assertEquals(7L, command.getValue().createdBy());
        verify(relations).attachIndexJob(3L, 1, 9L);
    }

    @Test
    void staleGenerationDoesNotAcquireLockOrWriteVectors() {
        KnowledgeDocumentRelation current = relation(3L, 4L, 5L, 2, 10L);
        when(relations.selectById(3L)).thenReturn(current);
        DocumentIndexJobPayload stale = new DocumentIndexJobPayload(
                3L, 4L, 5L, 1, new DocumentLinkConfig());

        String result = service.executeIndexJob(stale, 9L, executionContext);

        assertEquals("索引版本已过期，安全跳过", result);
        verifyNoInteractions(indexLock, vectors);
    }

    private KnowledgeDocumentRelation relation(Long id, Long documentId, Long knowledgeId,
                                                Integer revision, Long jobId) {
        KnowledgeDocumentRelation relation = new KnowledgeDocumentRelation();
        relation.setId(id);
        relation.setDocumentId(documentId);
        relation.setKnowledgeId(knowledgeId);
        relation.setIndexRevision(revision);
        relation.setJobId(jobId);
        relation.setProcessStatus(0);
        relation.setChunkMode("default");
        relation.setSplitType("length");
        return relation;
    }
}
