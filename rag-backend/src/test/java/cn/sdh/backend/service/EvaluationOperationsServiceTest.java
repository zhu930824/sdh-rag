package cn.sdh.backend.service;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.dto.EvaluationHumanReviewRequest;
import cn.sdh.backend.entity.EvaluationQa;
import cn.sdh.backend.entity.EvaluationTask;
import cn.sdh.backend.mapper.EvaluationQaMapper;
import cn.sdh.backend.mapper.EvaluationTaskMapper;
import cn.sdh.backend.service.impl.EvaluationOperationsServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationOperationsServiceTest {
    @Mock EvaluationQaMapper qaMapper;
    @Mock EvaluationTaskMapper taskMapper;
    @Mock KnowledgeAccessService access;
    EvaluationOperationsServiceImpl service;

    @BeforeEach void setup() {
        UserContext context = new UserContext();
        context.setUserId(7L);
        UserContext.setUserContext(context);
        service = new EvaluationOperationsServiceImpl(qaMapper, taskMapper, access);
    }

    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void categorySlicesExposeWeakBusinessSegment() {
        when(taskMapper.selectById(1L)).thenReturn(task());
        EvaluationQa salesGood = qa("销售", "0.8", null);
        EvaluationQa salesBad = qa("销售", "0.4", "RETRIEVAL_MISS");
        EvaluationQa support = qa("客服", "1.0", null);
        when(qaMapper.selectList(any())).thenReturn(List.of(salesGood, salesBad, support));

        var slices = service.slices(1L, "category");

        assertEquals(2, slices.size());
        var sales = slices.stream().filter(s -> "销售".equals(s.value())).findFirst().orElseThrow();
        assertEquals(2, sales.sampleCount());
        assertEquals(1, sales.failureCount());
        assertEquals(new BigDecimal("0.6000"), sales.recallAtK());
        verify(access).requireRead(9L, 7L);
    }

    @Test void humanReviewNormalizesFivePointScale() {
        EvaluationQa qa = qa("客服", "0.8", null);
        qa.setId(11L);
        qa.setTaskId(1L);
        when(qaMapper.selectById(11L)).thenReturn(qa);
        when(taskMapper.selectById(1L)).thenReturn(task());
        EvaluationHumanReviewRequest request = new EvaluationHumanReviewRequest();
        request.setFaithfulness(BigDecimal.valueOf(4));
        request.setOverallScore(BigDecimal.valueOf(3));
        request.setNote("回答遗漏退款时限");

        service.review(11L, request);

        assertEquals("COMPLETED", qa.getHumanReviewStatus());
        assertEquals(new BigDecimal("0.8000"), qa.getHumanFaithfulness());
        assertEquals(new BigDecimal("0.6000"), qa.getHumanOverallScore());
        assertEquals(7L, qa.getHumanReviewerId());
        assertNotNull(qa.getHumanReviewTime());
        verify(qaMapper).updateById(qa);
    }

    private EvaluationTask task() {
        EvaluationTask task = new EvaluationTask();
        task.setId(1L);
        task.setKnowledgeId(9L);
        return task;
    }

    private EvaluationQa qa(String category, String recall, String failure) {
        EvaluationQa qa = new EvaluationQa();
        qa.setTaskId(1L);
        qa.setCategory(category);
        qa.setRecallAtK(new BigDecimal(recall));
        qa.setFailureCategory(failure);
        return qa;
    }
}
