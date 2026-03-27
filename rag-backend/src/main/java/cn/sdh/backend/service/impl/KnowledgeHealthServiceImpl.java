package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.entity.KnowledgeHealth;
import cn.sdh.backend.mapper.KnowledgeDocumentMapper;
import cn.sdh.backend.mapper.KnowledgeHealthMapper;
import cn.sdh.backend.service.KnowledgeHealthService;
import cn.sdh.backend.service.DocumentChunkService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeHealthServiceImpl extends ServiceImpl<KnowledgeHealthMapper, KnowledgeHealth> implements KnowledgeHealthService {

    private final KnowledgeHealthMapper healthMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final DocumentChunkService chunkService;
    private final ObjectMapper objectMapper;

    @Override
    public List<KnowledgeHealth> getHealthHistory(Long knowledgeId) {
        return healthMapper.selectByKnowledgeId(knowledgeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeHealth checkDocumentCount(Long knowledgeId) {
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDocument::getKnowledgeId, knowledgeId);
        long totalDocs = documentMapper.selectCount(wrapper);

        wrapper.eq(KnowledgeDocument::getStatus, 1);
        long activeDocs = documentMapper.selectCount(wrapper);

        double score = calculateDocCountScore(totalDocs);
        List<String> suggestions = new ArrayList<>();

        if (totalDocs == 0) {
            suggestions.add("知识库为空，请上传文档");
            score = 0;
        } else if (totalDocs < 10) {
            suggestions.add("文档数量较少，建议上传更多相关文档以提高问答质量");
        } else if (totalDocs > 10000) {
            suggestions.add("文档数量较多，建议定期清理过时文档以保持检索效率");
        }

        Map<String, Object> details = new HashMap<>();
        details.put("totalDocuments", totalDocs);
        details.put("activeDocuments", activeDocs);
        details.put("inactiveDocuments", totalDocs - activeDocs);

        return createHealthRecord(knowledgeId, "document_count", score, details, suggestions);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeHealth checkEmbeddingQuality(Long knowledgeId) {
        int totalChunks = chunkService.countChunksByKnowledge(knowledgeId);
        int chunksWithEmbedding = chunkService.countEmbeddingsByKnowledge(knowledgeId);
        int chunksWithoutContent = chunkService.countEmptyChunksByKnowledge(knowledgeId);

        double embeddingRate = totalChunks > 0 ? (double) chunksWithEmbedding / totalChunks : 0;
        double emptyRate = totalChunks > 0 ? (double) chunksWithoutContent / totalChunks : 0;

        double score = (embeddingRate * 70 + (1 - emptyRate) * 30);

        List<String> suggestions = new ArrayList<>();
        if (embeddingRate < 0.8) {
            suggestions.add(String.format("仅%.1f%%的文档块有向量嵌入，建议重建索引", embeddingRate * 100));
        }
        if (emptyRate > 0.1) {
            suggestions.add(String.format("%.1f%%的文档块内容为空，请检查文档解析是否正常", emptyRate * 100));
        }

        Map<String, Object> details = new HashMap<>();
        details.put("totalChunks", totalChunks);
        details.put("chunksWithEmbedding", chunksWithEmbedding);
        details.put("chunksWithoutContent", chunksWithoutContent);
        details.put("embeddingRate", embeddingRate);
        details.put("emptyRate", emptyRate);

        return createHealthRecord(knowledgeId, "embedding_quality", score, details, suggestions);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeHealth checkIndexStatus(Long knowledgeId) {
        long indexSize = chunkService.getIndexSizeByKnowledge(knowledgeId);
        long lastUpdateTime = chunkService.getLastUpdateTimeByKnowledge(knowledgeId);

        long hoursSinceUpdate = (System.currentTimeMillis() - lastUpdateTime) / (1000 * 60 * 60);

        double score = 100;
        List<String> suggestions = new ArrayList<>();

        if (indexSize == 0) {
            score = 0;
            suggestions.add("索引为空，请执行文档索引");
        } else if (hoursSinceUpdate > 24 * 7) {
            score = 60;
            suggestions.add("索引已超过7天未更新，建议重新索引");
        } else if (hoursSinceUpdate > 24 * 30) {
            score = 30;
            suggestions.add("索引已超过30天未更新，强烈建议重新索引");
        }

        Map<String, Object> details = new HashMap<>();
        details.put("indexSize", indexSize);
        details.put("lastUpdateHours", hoursSinceUpdate);
        details.put("lastUpdateTime", new Date(lastUpdateTime));

        return createHealthRecord(knowledgeId, "index_status", score, details, suggestions);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> runFullHealthCheck(Long knowledgeId) {
        log.info("开始知识库健康检查: {}", knowledgeId);

        Map<String, Object> result = new HashMap<>();
        List<KnowledgeHealth> checks = new ArrayList<>();

        checks.add(checkDocumentCount(knowledgeId));
        checks.add(checkEmbeddingQuality(knowledgeId));
        checks.add(checkIndexStatus(knowledgeId));

        double overallScore = getOverallHealthScore(knowledgeId);

        result.put("checks", checks);
        result.put("overallScore", overallScore);
        result.put("healthLevel", getHealthLevel(overallScore));
        result.put("checkTime", LocalDateTime.now());

        log.info("知识库健康检查完成: {} - 总分: {}", knowledgeId, overallScore);
        return result;
    }

    @Override
    public List<Map<String, Object>> getHealthSummary() {
        List<Map<String, Object>> summary = new ArrayList<>();

        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(KnowledgeDocument::getKnowledgeId)
               .groupBy(KnowledgeDocument::getKnowledgeId);
        List<KnowledgeDocument> docs = documentMapper.selectList(wrapper);

        for (KnowledgeDocument doc : docs) {
            Long knowledgeId = doc.getKnowledgeId();
            if (knowledgeId == null) continue;

            Map<String, Object> item = new HashMap<>();
            item.put("knowledgeId", knowledgeId);
            item.put("overallScore", getOverallHealthScore(knowledgeId));

            KnowledgeHealth latest = healthMapper.selectLatestByType(knowledgeId, "document_count");
            if (latest != null) {
                item.put("lastCheckTime", latest.getCheckTime());
            }

            summary.add(item);
        }

        return summary;
    }

    @Override
    public void scheduleHealthCheck(Long knowledgeId) {
        log.info("计划执行知识库健康检查: {}", knowledgeId);
        runFullHealthCheck(knowledgeId);
    }

    @Override
    public double getOverallHealthScore(Long knowledgeId) {
        Double avgScore = healthMapper.selectAverageScore(knowledgeId);
        if (avgScore == null) {
            Map<String, Object> check = runFullHealthCheck(knowledgeId);
            return (double) check.get("overallScore");
        }
        return avgScore;
    }

    private KnowledgeHealth createHealthRecord(Long knowledgeId, String checkType, double score, 
            Map<String, Object> details, List<String> suggestions) {
        KnowledgeHealth health = new KnowledgeHealth();
        health.setKnowledgeId(knowledgeId);
        health.setCheckType(checkType);
        health.setScore(BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP));
        health.setCheckTime(LocalDateTime.now());

        try {
            health.setDetails(objectMapper.writeValueAsString(details));
            health.setSuggestions(objectMapper.writeValueAsString(suggestions));
        } catch (JsonProcessingException e) {
            log.error("序列化健康检查详情失败", e);
        }

        save(health);
        return health;
    }

    private double calculateDocCountScore(long count) {
        if (count == 0) return 0;
        if (count < 5) return 40;
        if (count < 20) return 60;
        if (count < 100) return 80;
        if (count < 1000) return 90;
        return 100;
    }

    private String getHealthLevel(double score) {
        if (score >= 90) return "优秀";
        if (score >= 75) return "良好";
        if (score >= 60) return "一般";
        if (score >= 40) return "较差";
        return "危险";
    }
}
