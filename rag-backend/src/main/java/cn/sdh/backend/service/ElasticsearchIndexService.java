package cn.sdh.backend.service;

/**
 * Elasticsearch 索引管理服务
 * 负责知识库级别 ES 索引的动态创建和删除
 */
public interface ElasticsearchIndexService {

    /**
     * 获取知识库对应的 ES 索引名
     * @param knowledgeId 知识库ID
     * @return 索引名
     */
    String getIndexName(Long knowledgeId);

    /**
     * 为知识库创建 ES 索引
     * 索引的向量维度根据 embedding 模型确定
     * @param knowledgeId 知识库ID
     * @param embeddingModelName 嵌入模型名称
     */
    void createIndexForKnowledgeBase(Long knowledgeId, String embeddingModelName);

    /**
     * 删除知识库的 ES 索引
     * @param knowledgeId 知识库ID
     */
    void deleteIndexForKnowledgeBase(Long knowledgeId);

    /**
     * 检查知识库的 ES 索引是否存在
     * @param knowledgeId 知识库ID
     * @return 是否存在
     */
    boolean indexExists(Long knowledgeId);

    /**
     * 确保知识库的 ES 索引存在，不存在则创建
     * @param knowledgeId 知识库ID
     * @param embeddingModelName 嵌入模型名称
     */
    void ensureIndexExists(Long knowledgeId, String embeddingModelName);
}
