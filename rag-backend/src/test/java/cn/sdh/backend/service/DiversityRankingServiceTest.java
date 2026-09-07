package cn.sdh.backend.service;

import cn.sdh.backend.dto.SearchResult.RankedChunk;
import cn.sdh.backend.service.impl.EmbeddingServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiversityRankingServiceTest {

    private final EmbeddingServiceImpl embeddingService = mock(EmbeddingServiceImpl.class);
    private final DiversityRankingService diversityRankingService =
        new DiversityRankingService(embeddingService);

    @Test
    void testMmrSelect_ShouldReturnTopKResults() {
        List<RankedChunk> candidates = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            candidates.add(RankedChunk.builder()
                .documentId((long) i)
                .documentTitle("Doc " + i)
                .content("Content number " + i + " with unique text")
                .relevanceScore(0.9 - i * 0.05)
                .freshnessScore(1.0)
                .build());
        }
        when(embeddingService.calculateSimilarity(org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString())).thenReturn(0.1);

        List<RankedChunk> result = diversityRankingService.mmrSelect(candidates, 5, 0.7);

        assertNotNull(result);
        assertTrue(result.size() <= 5);
        assertTrue(result.size() > 0);
    }

    @Test
    void testMmrSelect_EmptyInput_ShouldReturnEmpty() {
        List<RankedChunk> result = diversityRankingService.mmrSelect(List.of(), 5, 0.7);
        assertTrue(result.isEmpty());
    }

    @Test
    void testMmrSelect_LessThanTopK_ShouldReturnAll() {
        List<RankedChunk> candidates = List.of(
            RankedChunk.builder().documentId(1L).relevanceScore(0.9).build(),
            RankedChunk.builder().documentId(2L).relevanceScore(0.8).build()
        );

        List<RankedChunk> result = diversityRankingService.mmrSelect(candidates, 5, 0.7);
        assertEquals(2, result.size());
    }
}
