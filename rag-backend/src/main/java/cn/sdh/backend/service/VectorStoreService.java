package cn.sdh.backend.service;

import cn.sdh.backend.entity.KnowledgeChunk;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 向量存储服务接口
 */
public interface VectorStoreService {

    /**
     * 添加向量（使用知识库配置的嵌入模型）
     * @param chunk 分块信息
     * @param knowledgeId 知识库ID
     * @return 向量ID
     */
    String addVector(KnowledgeChunk chunk, Long knowledgeId);

    /**
     * 添加向量（指定嵌入模型名称）
     * @param chunk 分块信息
     * @param knowledgeId 知识库ID
     * @param embeddingModelName 嵌入模型名称
     * @return 向量ID
     */
    String addVector(KnowledgeChunk chunk, Long knowledgeId, String embeddingModelName);

    /**
     * 批量添加向量（使用知识库配置的嵌入模型）
     * @param chunks 分块列表
     * @param knowledgeId 知识库ID
     * @return 向量ID列表
     */
    List<String> batchAddVectors(List<KnowledgeChunk> chunks, Long knowledgeId);

    /**
     * 批量添加向量（指定嵌入模型名称）
     * @param chunks 分块列表
     * @param knowledgeId 知识库ID
     * @param embeddingModelName 嵌入模型名称
     * @return 向量ID列表
     */
    List<String> batchAddVectors(List<KnowledgeChunk> chunks, Long knowledgeId, String embeddingModelName);

    /**
     * 更新向量的知识库关联
     * @param vectorId 向量ID
     * @param knowledgeId 要添加的知识库ID
     */
    void addKnowledgeToVector(String vectorId, Long knowledgeId);

    /**
     * 从向量中移除知识库关联
     * @param vectorId 向量ID
     * @param knowledgeId 要移除的知识库ID
     */
    void removeKnowledgeFromVector(String vectorId, Long knowledgeId);

    /**
     * 删除向量
     * @param vectorId 向量ID
     */
    void deleteVector(String vectorId);

    /**
     * 批量删除向量
     * @param vectorIds 向量ID列表
     */
    void batchDeleteVectors(List<String> vectorIds);

    /**
     * 按知识库ID删除所有向量
     * @param knowledgeId 知识库ID
     */
    void deleteVectorsByKnowledgeId(Long knowledgeId);

    /**
     * 相似度搜索（使用知识库配置的嵌入模型）
     * @param query 查询文本
     * @param knowledgeId 知识库ID
     * @param topK 返回数量
     * @return 相似文档列表
     */
    List<Document> similaritySearch(String query, Long knowledgeId, int topK);

    /**
     * 相似度搜索（指定嵌入模型名称）
     * @param query 查询文本
     * @param knowledgeId 知识库ID
     * @param topK 返回数量
     * @param embeddingModelName 嵌入模型名称
     * @return 相似文档列表
     */
    List<Document> similaritySearch(String query, Long knowledgeId, int topK, String embeddingModelName);

    /**
     * 相似度搜索（带阈值过滤）
     * @param query 查询文本
     * @param knowledgeId 知识库ID
     * @param topK 返回数量
     * @param embeddingModelName 嵌入模型名称
     * @param minScore 最小相似度阈值（0-1），低于此值的结果会被过滤
     * @return 相似文档列表
     */
    List<Document> similaritySearch(String query, Long knowledgeId, int topK, String embeddingModelName, Double minScore);

    /**
     * 按文档ID删除所有向量
     * @param documentId 文档ID
     */
    void deleteVectorsByDocumentId(Long documentId);

    /**
     * 按文档ID和知识库ID删除向量
     * @param documentId 文档ID
     * @param knowledgeId 知识库ID
     */
    void deleteVectorsByDocumentIdAndKnowledgeId(Long documentId, Long knowledgeId);

    // ==================== 分块查询方法（从ES查询） ====================

    /**
     * 统计知识库的分块数量
     * @param knowledgeId 知识库ID
     * @return 分块数量
     */
    long countChunksByKnowledgeId(Long knowledgeId);

    /**
     * 统计文档的分块数量
     * @param documentId 文档ID
     * @param knowledgeId 知识库ID
     * @return 分块数量
     */
    long countChunksByDocumentId(Long documentId, Long knowledgeId);

    /**
     * 获取知识库的分块列表（分页）
     * @param knowledgeId 知识库ID
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 分块列表
     */
    List<Document> getChunksByKnowledgeId(Long knowledgeId, int page, int size);

    /**
     * 获取文档的分块列表（分页）
     * @param documentId 文档ID
     * @param knowledgeId 知识库ID
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 分块列表
     */
    List<Document> getChunksByDocumentId(Long documentId, Long knowledgeId, int page, int size);

    /**
     * 根据分块ID获取分块详情
     * @param vectorId 向量ID
     * @return 分块文档
     */
    Document getChunkById(String vectorId);

    /**
     * 获取知识库的索引大小（字节数）
     * @param knowledgeId 知识库ID
     * @return 索引大小
     */
    long getIndexSizeByKnowledgeId(Long knowledgeId);

    /**
     * 获取知识库最后更新时间
     * @param knowledgeId 知识库ID
     * @return 时间戳（毫秒）
     */
    long getLastUpdateTimeByKnowledgeId(Long knowledgeId);

    /**
     * 关键字搜索（BM25）
     * @param query 查询关键字
     * @param knowledgeId 知识库ID
     * @param topK 返回数量
     * @return 匹配的文档列表
     */
    List<Document> keywordSearch(String query, Long knowledgeId, int topK);

    /**
     * 混合检索（向量 + 关键字）
     * @param query 查询文本
     * @param knowledgeId 知识库ID
     * @param vectorTopK 向量检索数量
     * @param keywordTopK 关键字检索数量
     * @param vectorWeight 向量权重
     * @param keywordWeight 关键字权重
     * @param embeddingModelName 嵌入模型名称
     * @return 混合检索结果
     */
    List<Document> hybridSearch(String query, Long knowledgeId, int vectorTopK, int keywordTopK,
                                double vectorWeight, double keywordWeight, String embeddingModelName);

    /**
     * 混合检索（带相似度阈值过滤）
     * @param query 查询文本
     * @param knowledgeId 知识库ID
     * @param vectorTopK 向量检索数量
     * @param keywordTopK 关键字检索数量
     * @param vectorWeight 向量权重
     * @param keywordWeight 关键字权重
     * @param embeddingModelName 嵌入模型名称
     * @param minScore 最小相似度阈值（0-1）
     * @return 混合检索结果
     */
    List<Document> hybridSearch(String query, Long knowledgeId, int vectorTopK, int keywordTopK,
                                double vectorWeight, double keywordWeight, String embeddingModelName, Double minScore);
}
