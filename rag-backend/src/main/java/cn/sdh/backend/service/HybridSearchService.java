package cn.sdh.backend.service;

import cn.sdh.backend.entity.DocumentChunk;
import java.util.List;
import java.util.Map;

public interface HybridSearchService {

    List<Map<String, Object>> hybridSearch(Long knowledgeId, String query, int topK, double keywordWeight, double semanticWeight);

    List<DocumentChunk> keywordSearch(Long knowledgeId, String query, int topK);

    List<Map<String, Object>> semanticSearch(Long knowledgeId, String query, int topK);

    double calculateBM25Score(String query, String document);

    double calculateRelevanceScore(String query, DocumentChunk chunk);

    List<Map<String, Object>> rerankResults(List<Map<String, Object>> results, String query);

    Map<String, Object> searchWithFilters(Long knowledgeId, String query, Map<String, Object> filters, int page, int pageSize);

    void buildKeywordIndex(Long knowledgeId);

    void updateKeywordIndex(Long documentId);
}
