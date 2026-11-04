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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
    @Test void cascadeKeepsTheIndexedGenerationForEveryReturnedChunk() {
        RagSearchServiceImpl service = new RagSearchServiceImpl(knowledgeBaseService, vectorStoreService, rerankService,
                chatService, summaryIndexService, diversityRankingService, postProcessingService);
        KnowledgeBase kb = new KnowledgeBase(); kb.setId(7L); kb.setVectorTopK(10); kb.setKeywordTopK(10);
        kb.setVectorWeight(.7); kb.setKeywordWeight(.3); kb.setSimilarityThreshold(.1);
        var document = new org.springframework.ai.document.Document("indexed text", Map.of("id", "vector-42", "document_id", 42L,
                "index_revision", 3, "indexed_content_sha256", "a".repeat(64), "chunk_content_sha256", "79db5b6ba12bffb3af6679dcacdb7b4685d3336ccb1aee28c3220694d61eb6ad"));
        when(vectorStoreService.hybridSearch(anyString(), eq(7L), anyInt(), anyInt(), anyDouble(), anyDouble(), any(), any(), anyMap())).thenReturn(List.of(document));
        when(diversityRankingService.mmrSelect(anyList(), anyInt(), anyDouble())).thenAnswer(call -> call.getArgument(0));
        when(postProcessingService.process(anyList(), anyDouble(), anyDouble(), anyInt())).thenAnswer(call -> call.getArgument(0));
        SearchResult result = service.searchWithCascade("query", kb, List.of(), Map.of());
        var chunk = result.getChunks().getFirst();
        assertEquals("vector-42", chunk.getVectorId());
        assertEquals(3, chunk.getIndexRevision()); assertEquals("a".repeat(64), chunk.getIndexedContentSha256());
        assertTrue(chunk.getIndexedChunkHashVerified());
    }
}
