package cn.sdh.backend.service;

import cn.sdh.backend.dto.SearchResult.RankedChunk;
import cn.sdh.backend.service.impl.EmbeddingServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * MMR (Maximum Marginal Relevance) 多样性重排服务
 * 贪心选取每次最大化边际相关性的结果，去除语义重复的片段
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiversityRankingService {

    private final EmbeddingServiceImpl embeddingService;

    /**
     * MMR 算法贪心选取
     * @param candidates 精排后的候选 chunk 列表（按相关性降序）
     * @param topK       最终返回数量
     * @param lambda     相关性权重（0-1），越大越偏向相关性
     * @return 选取的最终 chunk 列表
     */
    public List<RankedChunk> mmrSelect(List<RankedChunk> candidates, int topK, double lambda) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        if (candidates.size() <= topK) return new ArrayList<>(candidates);

        List<RankedChunk> selected = new ArrayList<>();
        List<RankedChunk> remaining = new ArrayList<>(candidates);

        // 第一步：选相关性最高的
        selected.add(remaining.remove(0));

        // 贪心迭代：每次选 MMR 最大的
        while (selected.size() < topK && !remaining.isEmpty()) {
            double bestMmr = Double.NEGATIVE_INFINITY;
            int bestIdx = -1;

            for (int i = 0; i < remaining.size(); i++) {
                RankedChunk candidate = remaining.get(i);
                double relevance = candidate.getRelevanceScore() != null
                    ? candidate.getRelevanceScore() : 0.5;

                // 计算与已选结果的最大语义相似度
                double maxSimilarity = 0;
                for (RankedChunk sel : selected) {
                    double sim = embeddingService.calculateSimilarity(
                        candidate.getContent(), sel.getContent());
                    maxSimilarity = Math.max(maxSimilarity, sim);
                }

                double mmr = lambda * relevance - (1 - lambda) * maxSimilarity;
                if (mmr > bestMmr) {
                    bestMmr = mmr;
                    bestIdx = i;
                }
            }

            if (bestIdx >= 0) {
                selected.add(remaining.remove(bestIdx));
            } else {
                break;
            }
        }

        log.debug("MMR 从 {} 个候选中选取 {} 个结果, lambda={}",
            candidates.size(), selected.size(), lambda);
        return selected;
    }
}
