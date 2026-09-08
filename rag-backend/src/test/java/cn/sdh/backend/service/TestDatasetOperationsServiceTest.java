package cn.sdh.backend.service;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.entity.ChatHistory;
import cn.sdh.backend.entity.QaFeedback;
import cn.sdh.backend.entity.TestDataset;
import cn.sdh.backend.entity.TestDatasetItem;
import cn.sdh.backend.mapper.*;
import cn.sdh.backend.service.impl.TestDatasetServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestDatasetOperationsServiceTest {
    @Mock TestDatasetMapper datasets;
    @Mock TestDatasetItemMapper items;
    @Mock QaFeedbackMapper feedbacks;
    @Mock ChatHistoryMapper chats;
    @Mock KnowledgeAccessService access;
    TestDatasetServiceImpl service;

    @BeforeEach void setup() {
        UserContext context = new UserContext();
        context.setUserId(7L);
        UserContext.setUserContext(context);
        service = new TestDatasetServiceImpl(datasets, items, new ObjectMapper(), feedbacks, chats, access);
    }

    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void onlineFailureIsPromotedOnceAndBumpsDatasetVersion() {
        TestDataset dataset = new TestDataset();
        dataset.setId(10L);
        dataset.setDatasetType("custom");
        dataset.setVersion(3);
        dataset.setItemCount(2);
        dataset.setNegativeCount(0);
        when(datasets.selectById(10L)).thenReturn(dataset);

        QaFeedback feedback = new QaFeedback();
        feedback.setId(20L);
        feedback.setChatHistoryId(30L);
        feedback.setCorrectAnswer("人工确认的正确答案");
        when(feedbacks.selectById(20L)).thenReturn(feedback);
        ChatHistory chat = new ChatHistory();
        chat.setId(30L);
        chat.setKnowledgeId(9L);
        chat.setQuestion("真实客户问题");
        when(chats.selectById(30L)).thenReturn(chat);
        doAnswer(invocation -> {
            TestDatasetItem item = invocation.getArgument(0);
            item.setId(40L);
            return 1;
        }).when(items).insert(any(TestDatasetItem.class));

        TestDatasetItem result = service.addFeedbackItem(10L, 20L);

        assertEquals(40L, result.getId());
        assertEquals("真实客户问题", result.getQuestion());
        assertEquals("人工确认的正确答案", result.getExpectedAnswer());
        assertEquals("production_feedback", result.getCategory());
        assertEquals(4, dataset.getVersion());
        assertEquals(1, feedback.getStatus());
        verify(access).requireRead(9L, 7L);
        verify(feedbacks).updateById(feedback);
    }
}
