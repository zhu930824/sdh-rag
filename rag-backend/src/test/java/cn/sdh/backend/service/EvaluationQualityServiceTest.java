package cn.sdh.backend.service;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.entity.EvaluationQa;
import cn.sdh.backend.entity.EvaluationTask;
import cn.sdh.backend.mapper.EvaluationQaMapper;
import cn.sdh.backend.mapper.EvaluationTaskMapper;
import cn.sdh.backend.service.impl.EvaluationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationQualityServiceTest {
    @Mock EvaluationTaskMapper tasks;
    @Mock EvaluationQaMapper qaMapper;
    @Mock VectorStoreService vectors;
    @Mock KnowledgeBaseService bases;
    @Mock RagSearchService rag;
    @Mock ChatService chat;
    @Mock TestDatasetService datasets;
    @Mock KnowledgeAccessService access;
    EvaluationServiceImpl service;

    @BeforeEach void setup() {
        UserContext context = new UserContext();
        context.setUserId(8L);
        UserContext.setUserContext(context);
        service = new EvaluationServiceImpl(tasks, qaMapper, vectors, bases, rag, chat, datasets,
                new ObjectMapper(), access, Runnable::run);
    }

    @AfterEach void clearContext() { UserContext.clear(); }

    @Test void qualityReportClassifiesBusinessFailuresAndRecommendations() {
        EvaluationTask task = new EvaluationTask();
        task.setId(1L);
        task.setKnowledgeId(7L);
        when(tasks.selectList(any())).thenReturn(List.of(task));
        EvaluationQa unlabeled = qa(15L, false, false, false, "[\"d\"]", "open");
        unlabeled.setSourceChunkId(null);
        unlabeled.setSourceDocumentId(null);
        EvaluationQa hallucination = qa(16L, false, true, true, "[\"e\"]", "open");
        hallucination.setSourceChunkId(null);
        hallucination.setSourceDocumentId(null);
        hallucination.setFailureCategory("HALLUCINATION");
        when(qaMapper.selectList(any())).thenReturn(List.of(
                qa(11L, false, false, false, "[]", "open"),
                qa(12L, false, false, true, "[\"a\"]", "resolved"),
                qa(13L, true, false, false, "[\"b\"]", "ignored"),
                qa(14L, false, true, true, "[\"c\"]", "open"),
                unlabeled,
                hallucination
        ));

        var report = service.getQualityReport(7L, 30);

        assertEquals(6, report.totalQuestions());
        assertEquals(2, report.chunkMisses());
        assertEquals(1, report.documentMisses());
        assertEquals(1, report.negativeFalsePositives());
        assertEquals(2, report.openIssues());
        assertEquals(4, report.issues().size());
        assertEquals(3, report.recommendations().size());
        verify(access).requireRead(7L, 8L);
    }

    @Test void editorCanResolveIssueWithAuditFields() {
        EvaluationQa qa = qa(11L, false, false, false, "[]", "open");
        EvaluationTask task = new EvaluationTask();
        task.setId(1L);
        task.setKnowledgeId(7L);
        when(qaMapper.selectById(11L)).thenReturn(qa);
        when(tasks.selectById(1L)).thenReturn(task);

        service.updateIssue(11L, "resolved", "已补充标准操作手册");

        verify(access).requireEdit(7L, 8L);
        assertEquals("resolved", qa.getIssueStatus());
        assertEquals(8L, qa.getResolvedBy());
        assertNotNull(qa.getResolvedTime());
        verify(qaMapper).updateById(qa);
    }

    private EvaluationQa qa(Long id, boolean negative, boolean hit, boolean docHit, String retrieved, String status) {
        EvaluationQa qa = new EvaluationQa();
        qa.setId(id);
        qa.setTaskId(1L);
        qa.setQuestion("question-" + id);
        qa.setIsNegative(negative);
        qa.setHit(hit);
        qa.setDocHit(docHit);
        qa.setRetrievedChunkIds(retrieved);
        qa.setIssueStatus(status);
        if (!negative) {
            qa.setSourceChunkId("chunk-" + id);
            qa.setSourceDocumentId(id);
        }
        return qa;
    }
}
