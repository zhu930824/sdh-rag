package cn.sdh.backend.service;

import cn.sdh.backend.dto.SearchResult;
import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.service.impl.RagSearchServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagSearchServiceImplTest {

    @Mock private KnowledgeBaseService knowledgeBaseService;
    @Mock private VectorStoreService vectorStoreService;
    @Mock private RerankService rerankService;
    @Mock private ChatService chatService;
    @Mock private SummaryIndexService summaryIndexService;
    @Mock private DiversityRankingService diversityRankingService;
    @Mock private PostProcessingService postProcessingService;

    @Test
    void cascadeShouldFallbackToFilteredHybridSearchWhenSummaryIndexMisses() {
        RagSearchServiceImpl service = new RagSearchServiceImpl(
            knowledgeBaseService, vectorStoreService, rerankService, chatService,
            summaryIndexService, diversityRankingService, postProcessingService);

        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(7L);
        kb.setEnableSummaryIndex(true);
        kb.setEmbeddingModel("embedding-model");
        kb.setVectorTopK(10);
        kb.setKeywordTopK(10);
        kb.setVectorWeight(0.7);
        kb.setKeywordWeight(0.3);
        kb.setSimilarityThreshold(0.6);

        Map<String, Object> filters = Map.of("department", "研发部");
        when(summaryIndexService.coarseRetrieval(eq("问题"), eq(7L), anyInt(),
            eq("embedding-model"), eq(filters))).thenReturn(Map.of());
        when(vectorStoreService.hybridSearch(eq("问题"), eq(7L), anyInt(), anyInt(),
            anyDouble(), anyDouble(), eq("embedding-model"), eq(0.6), eq(filters)))
            .thenReturn(List.of());

        SearchResult result = service.searchWithCascade("问题", kb, List.of(), filters);

        assertFalse(result.isSuccess());
        verify(vectorStoreService).hybridSearch(eq("问题"), eq(7L), anyInt(), anyInt(),
            anyDouble(), anyDouble(), eq("embedding-model"), eq(0.6), eq(filters));
    }
}
