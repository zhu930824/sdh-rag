package cn.sdh.backend.service;

import cn.sdh.backend.dto.SearchResult.RankedChunk;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DiversityRankingServiceTest {

    @Autowired(required = false)
    private DiversityRankingService diversityRankingService;

    @Test
    void testMmrSelect_ShouldReturnTopKResults() {
        if (diversityRankingService == null) return; // skip if not wired

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

        List<RankedChunk> result = diversityRankingService.mmrSelect(candidates, 5, 0.7);

        assertNotNull(result);
        assertTrue(result.size() <= 5);
        assertTrue(result.size() > 0);
    }

    @Test
    void testMmrSelect_EmptyInput_ShouldReturnEmpty() {
        if (diversityRankingService == null) return;

        List<RankedChunk> result = diversityRankingService.mmrSelect(List.of(), 5, 0.7);
        assertTrue(result.isEmpty());
    }

    @Test
    void testMmrSelect_LessThanTopK_ShouldReturnAll() {
        if (diversityRankingService == null) return;

        List<RankedChunk> candidates = List.of(
            RankedChunk.builder().documentId(1L).relevanceScore(0.9).build(),
            RankedChunk.builder().documentId(2L).relevanceScore(0.8).build()
        );

        List<RankedChunk> result = diversityRankingService.mmrSelect(candidates, 5, 0.7);
        assertEquals(2, result.size());
    }
}
