package cn.sdh.backend.service;

import cn.sdh.backend.entity.DocumentChunk;
import java.util.List;
import java.util.Map;

public interface HybridSearchService {

    /**
     * 混合检索（使用知识库配置）
     * @param knowledgeId 知识库ID
     * @param query 查询文本
     * @return 检索结果
     */
    List<Map<String, Object>> hybridSearchWithKnowledgeBaseConfig(Long knowledgeId, String query);

    /**
     * 混合检索
     * @param knowledgeId 知识库ID
     * @param query 查询文本
     * @param topK 返回数量
     * @param keywordWeight 关键字权重
     * @param semanticWeight 语义权重
     * @return 检索结果
     */
    List<Map<String, Object>> hybridSearch(Long knowledgeId, String query, int topK, double keywordWeight, double semanticWeight);

    /**
     * 带相似度阈值的混合检索
     * @param knowledgeId 知识库ID
     * @param query 查询文本
     * @param topK 返回数量
     * @param keywordWeight 关键字权重
     * @param semanticWeight 语义权重
     * @param similarityThreshold 相似度阈值
     * @return 检索结果
     */
    List<Map<String, Object>> hybridSearchWithThreshold(Long knowledgeId, String query, int topK,
            double keywordWeight, double semanticWeight, double similarityThreshold);

    List<DocumentChunk> keywordSearch(Long knowledgeId, String query, int topK);

    List<Map<String, Object>> semanticSearch(Long knowledgeId, String query, int topK);

    double calculateBM25Score(String query, String document);

    double calculateRelevanceScore(String query, DocumentChunk chunk);

    List<Map<String, Object>> rerankResults(List<Map<String, Object>> results, String query);

    Map<String, Object> searchWithFilters(Long knowledgeId, String query, Map<String, Object> filters, int page, int pageSize);

    void buildKeywordIndex(Long knowledgeId);

    void updateKeywordIndex(Long documentId);

    /**
     * 多轮对话改写查询
     * @param query 当前查询
     * @param chatHistory 对话历史
     * @return 改写后的查询
     */
    String rewriteQuery(String query, List<Map<String, String>> chatHistory);
}
