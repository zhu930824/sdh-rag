package cn.sdh.backend.service;

import cn.sdh.backend.config.FreshnessConfig;
import cn.sdh.backend.dto.SearchResult.RankedChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 检索后处理服务：硬阈值截断 + 时间衰减重排
 */
@Slf4j
@Service
public class PostProcessingService {

    /**
     * 后处理管线
     * @param chunks      精排后的候选 chunk
     * @param threshold   相似度硬阈值
     * @param recencyAlpha 时间衰减系数 (0-1)
     * @param finalTopK   最终返回数量
     * @return 通过筛选的最终结果
     */
    public List<RankedChunk> process(List<RankedChunk> chunks, double threshold,
                                      double recencyAlpha, int finalTopK) {

        // Step 1: 硬阈值截断
        List<RankedChunk> passed = chunks.stream()
            .filter(c -> c.getRelevanceScore() != null && c.getRelevanceScore() >= threshold)
            .collect(Collectors.toList());

        int filteredCount = chunks.size() - passed.size();
        if (filteredCount > 0) {
            log.info("硬阈值截断: {} 个结果低于阈值 {} 被过滤", filteredCount, threshold);
        }

        // Step 2: 全部截断 → 返回空列表，由上层生成降级消息
        if (passed.isEmpty()) {
            log.warn("所有检索结果均低于相似度阈值 {}，返回空", threshold);
            return List.of();
        }

        // Step 3: 时间衰减加权
        for (RankedChunk chunk : passed) {
            double relevance = chunk.getRelevanceScore() != null ? chunk.getRelevanceScore() : 0.5;
            double freshness = chunk.getFreshnessScore() != null
                ? chunk.getFreshnessScore()
                : FreshnessConfig.MIN_FRESHNESS;
            // finalScore = relevance × (1 - recencyAlpha + recencyAlpha × freshness)
            double finalScore = relevance * ((1 - recencyAlpha) + recencyAlpha * freshness);
            chunk.setFinalScore(finalScore);
        }

        // Step 4: 按最终分重排
        passed.sort(Comparator.comparingDouble(RankedChunk::getFinalScore).reversed());

        // Step 5: 截断到 finalTopK
        if (passed.size() <= finalTopK) {
            return passed;
        }
        return passed.subList(0, finalTopK);
    }
}
