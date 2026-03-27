package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.DocumentChunk;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.mapper.DocumentChunkMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentMapper;
import cn.sdh.backend.service.HybridSearchService;
import cn.sdh.backend.service.EmbeddingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchServiceImpl implements HybridSearchService {

    private final DocumentChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    private static final double DEFAULT_KEYWORD_WEIGHT = 0.3;
    private static final double DEFAULT_SEMANTIC_WEIGHT = 0.7;
    private static final double BM25_K1 = 1.2;
    private static final double BM25_B = 0.75;

    @Override
    public List<Map<String, Object>> hybridSearch(Long knowledgeId, String query, int topK, 
            double keywordWeight, double semanticWeight) {
        log.info("执行混合搜索: knowledgeId={}, query={}, topK={}", knowledgeId, query, topK);

        if (keywordWeight + semanticWeight != 1.0) {
            keywordWeight = DEFAULT_KEYWORD_WEIGHT;
            semanticWeight = DEFAULT_SEMANTIC_WEIGHT;
        }

        List<DocumentChunk> keywordResults = keywordSearch(knowledgeId, query, topK * 2);
        List<Map<String, Object>> semanticResults = semanticSearch(knowledgeId, query, topK * 2);

        Map<Long, Map<String, Object>> mergedResults = new HashMap<>();

        for (DocumentChunk chunk : keywordResults) {
            Map<String, Object> item = mergedResults.computeIfAbsent(chunk.getId(), k -> new HashMap<>());
            item.put("chunk", chunk);
            item.put("keywordScore", calculateBM25Score(query, chunk.getContent()));
            item.put("semanticScore", 0.0);
        }

        for (Map<String, Object> result : semanticResults) {
            DocumentChunk chunk = (DocumentChunk) result.get("chunk");
            Map<String, Object> item = mergedResults.computeIfAbsent(chunk.getId(), k -> new HashMap<>());
            item.put("chunk", chunk);
            item.putIfAbsent("keywordScore", 0.0);
            item.put("semanticScore", result.get("score"));
        }

        for (Map<String, Object> item : mergedResults.values()) {
            double keywordScore = ((Number) item.getOrDefault("keywordScore", 0.0)).doubleValue();
            double semanticScore = ((Number) item.getOrDefault("semanticScore", 0.0)).doubleValue();
            
            double normalizedKeyword = normalizeScore(keywordScore, 0, 10);
            double normalizedSemantic = normalizeScore(semanticScore, 0, 1);
            
            double finalScore = keywordWeight * normalizedKeyword + semanticWeight * normalizedSemantic;
            item.put("finalScore", finalScore);
        }

        List<Map<String, Object>> sortedResults = mergedResults.values().stream()
            .sorted((a, b) -> Double.compare(
                ((Number) b.get("finalScore")).doubleValue(),
                ((Number) a.get("finalScore")).doubleValue()
            ))
            .limit(topK)
            .collect(Collectors.toList());

        return rerankResults(sortedResults, query);
    }

    @Override
    public List<DocumentChunk> keywordSearch(Long knowledgeId, String query, int topK) {
        log.debug("执行关键词搜索: {}", query);

        LambdaQueryWrapper<DocumentChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(knowledgeId != null, DocumentChunk::getKnowledgeId, knowledgeId);

        String[] keywords = extractKeywords(query);
        if (keywords.length == 0) {
            return chunkMapper.selectList(wrapper.last("LIMIT " + topK));
        }

        for (String keyword : keywords) {
            wrapper.or().like(DocumentChunk::getContent, keyword);
        }

        wrapper.last("LIMIT " + topK);
        return chunkMapper.selectList(wrapper);
    }

    @Override
    public List<Map<String, Object>> semanticSearch(Long knowledgeId, String query, int topK) {
        log.debug("执行语义搜索: {}", query);

        float[] queryEmbedding = embeddingService.getEmbedding(query);
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            log.warn("无法获取查询向量");
            return Collections.emptyList();
        }

        LambdaQueryWrapper<DocumentChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(knowledgeId != null, DocumentChunk::getKnowledgeId, knowledgeId)
               .isNotNull(DocumentChunk::getEmbedding);

        List<DocumentChunk> chunks = chunkMapper.selectList(wrapper.last("LIMIT 1000"));

        List<Map<String, Object>> results = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            float[] chunkEmbedding = parseEmbedding(chunk.getEmbedding());
            if (chunkEmbedding != null) {
                double similarity = cosineSimilarity(queryEmbedding, chunkEmbedding);
                if (similarity > 0.3) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("chunk", chunk);
                    result.put("score", similarity);
                    results.add(result);
                }
            }
        }

        return results.stream()
            .sorted((a, b) -> Double.compare(
                ((Number) b.get("score")).doubleValue(),
                ((Number) a.get("score")).doubleValue()
            ))
            .limit(topK)
            .collect(Collectors.toList());
    }

    @Override
    public double calculateBM25Score(String query, String document) {
        if (query == null || document == null || query.isEmpty() || document.isEmpty()) {
            return 0.0;
        }

        String[] queryTerms = extractKeywords(query);
        String[] docTerms = document.toLowerCase().split("\\s+");

        Map<String, Integer> termFreq = new HashMap<>();
        for (String term : docTerms) {
            termFreq.merge(term, 1, Integer::sum);
        }

        int docLength = docTerms.length;
        double avgDocLength = getAverageDocumentLength();

        double score = 0.0;
        for (String term : queryTerms) {
            int tf = termFreq.getOrDefault(term, 0);
            if (tf > 0) {
                double idf = calculateIDF(term);
                double numerator = tf * (BM25_K1 + 1);
                double denominator = tf + BM25_K1 * (1 - BM25_B + BM25_B * docLength / avgDocLength);
                score += idf * numerator / denominator;
            }
        }

        return score;
    }

    @Override
    public double calculateRelevanceScore(String query, DocumentChunk chunk) {
        double keywordScore = calculateBM25Score(query, chunk.getContent());
        double semanticScore = 0.0;

        float[] queryEmbedding = embeddingService.getEmbedding(query);
        float[] chunkEmbedding = parseEmbedding(chunk.getEmbedding());

        if (queryEmbedding != null && chunkEmbedding != null) {
            semanticScore = cosineSimilarity(queryEmbedding, chunkEmbedding);
        }

        return 0.3 * keywordScore + 0.7 * semanticScore;
    }

    @Override
    public List<Map<String, Object>> rerankResults(List<Map<String, Object>> results, String query) {
        for (Map<String, Object> result : results) {
            DocumentChunk chunk = (DocumentChunk) result.get("chunk");
            
            double diversityPenalty = calculateDiversityPenalty(chunk, results);
            double currentScore = ((Number) result.get("finalScore")).doubleValue();
            result.put("finalScore", currentScore * (1 - diversityPenalty));
        }

        return results.stream()
            .sorted((a, b) -> Double.compare(
                ((Number) b.get("finalScore")).doubleValue(),
                ((Number) a.get("finalScore")).doubleValue()
            ))
            .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> searchWithFilters(Long knowledgeId, String query, 
            Map<String, Object> filters, int page, int pageSize) {
        log.info("执行带过滤器的搜索: knowledgeId={}, query={}", knowledgeId, query);

        LambdaQueryWrapper<DocumentChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChunk::getKnowledgeId, knowledgeId);

        if (filters != null) {
            if (filters.containsKey("documentId")) {
                wrapper.eq(DocumentChunk::getDocumentId, filters.get("documentId"));
            }
            if (filters.containsKey("categoryId")) {
                wrapper.eq(DocumentChunk::getCategoryId, filters.get("categoryId"));
            }
            if (filters.containsKey("startDate") && filters.containsKey("endDate")) {
                wrapper.between(DocumentChunk::getCreateTime, filters.get("startDate"), filters.get("endDate"));
            }
        }

        long total = chunkMapper.selectCount(wrapper);

        List<DocumentChunk> chunks = keywordSearch(knowledgeId, query, pageSize);

        List<Map<String, Object>> results = chunks.stream()
            .map(chunk -> {
                Map<String, Object> item = new HashMap<>();
                item.put("chunk", chunk);
                item.put("score", calculateBM25Score(query, chunk.getContent()));
                return item;
            })
            .sorted((a, b) -> Double.compare(
                ((Number) b.get("score")).doubleValue(),
                ((Number) a.get("score")).doubleValue()
            ))
            .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("records", results);
        response.put("total", total);
        response.put("page", page);
        response.put("pageSize", pageSize);

        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void buildKeywordIndex(Long knowledgeId) {
        log.info("构建关键词索引: {}", knowledgeId);

        LambdaQueryWrapper<DocumentChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChunk::getKnowledgeId, knowledgeId);
        List<DocumentChunk> chunks = chunkMapper.selectList(wrapper);

        for (DocumentChunk chunk : chunks) {
            chunk.setKeywords(extractKeywordsAsString(chunk.getContent()));
            chunkMapper.updateById(chunk);
        }

        log.info("关键词索引构建完成: {} 个文档块", chunks.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateKeywordIndex(Long documentId) {
        log.info("更新文档关键词索引: {}", documentId);

        LambdaQueryWrapper<DocumentChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChunk::getDocumentId, documentId);
        List<DocumentChunk> chunks = chunkMapper.selectList(wrapper);

        for (DocumentChunk chunk : chunks) {
            chunk.setKeywords(extractKeywordsAsString(chunk.getContent()));
            chunkMapper.updateById(chunk);
        }
    }

    private String[] extractKeywords(String text) {
        if (text == null || text.isEmpty()) return new String[0];

        Set<String> keywords = new HashSet<>();
        
        Pattern chinesePattern = Pattern.compile("[\\u4e00-\\u9fa5]{2,}");
        Matcher chineseMatcher = chinesePattern.matcher(text.toLowerCase());
        while (chineseMatcher.find()) {
            keywords.add(chineseMatcher.group());
        }

        Pattern englishPattern = Pattern.compile("[a-zA-Z]{3,}");
        Matcher englishMatcher = englishPattern.matcher(text.toLowerCase());
        while (englishMatcher.find()) {
            keywords.add(englishMatcher.group());
        }

        return keywords.toArray(new String[0]);
    }

    private String extractKeywordsAsString(String text) {
        return String.join(",", extractKeywords(text));
    }

    private float[] parseEmbedding(String embeddingStr) {
        if (embeddingStr == null || embeddingStr.isEmpty()) return null;

        try {
            return objectMapper.readValue(embeddingStr, float[].class);
        } catch (Exception e) {
            log.error("解析向量失败: {}", e.getMessage());
            return null;
        }
    }

    private double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) return 0.0;

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        if (norm1 == 0 || norm2 == 0) return 0.0;

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    private double normalizeScore(double score, double min, double max) {
        if (max == min) return 0.5;
        return Math.max(0, Math.min(1, (score - min) / (max - min)));
    }

    private double getAverageDocumentLength() {
        Long totalLength = chunkMapper.selectTotalContentLength();
        Long totalCount = chunkMapper.selectTotalCount();

        if (totalCount == null || totalCount == 0) return 500;
        return (double) (totalLength != null ? totalLength : 0) / totalCount;
    }

    private double calculateIDF(String term) {
        Long docCount = chunkMapper.selectCountContainingTerm(term);
        Long totalDocs = chunkMapper.selectTotalCount();

        if (docCount == null || docCount == 0 || totalDocs == null || totalDocs == 0) {
            return 1.0;
        }

        return Math.log((totalDocs - docCount + 0.5) / (docCount + 0.5) + 1);
    }

    private double calculateDiversityPenalty(DocumentChunk currentChunk, List<Map<String, Object>> results) {
        int similarCount = 0;
        Long currentDocId = currentChunk.getDocumentId();

        for (Map<String, Object> result : results) {
            DocumentChunk otherChunk = (DocumentChunk) result.get("chunk");
            if (otherChunk.getDocumentId().equals(currentDocId) && !otherChunk.getId().equals(currentChunk.getId())) {
                similarCount++;
            }
        }

        return Math.min(0.3, similarCount * 0.1);
    }
}
