package cn.sdh.backend.service;

import cn.sdh.backend.entity.KnowledgeChunk;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 向量存储服务接口
 */
public interface VectorStoreService {

    /**
     * 添加向量
     * @param chunk 分块信息
     * @param knowledgeId 知识库ID
     * @return 向量ID
     */
    String addVector(KnowledgeChunk chunk, Long knowledgeId);

    /**
     * 批量添加向量
     * @param chunks 分块列表
     * @param knowledgeId 知识库ID
     * @return 向量ID列表
     */
    List<String> batchAddVectors(List<KnowledgeChunk> chunks, Long knowledgeId);

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
     * 相似度搜索
     * @param query 查询文本
     * @param knowledgeId 知识库ID
     * @param topK 返回数量
     * @return 相似文档列表
     */
    List<Document> similaritySearch(String query, Long knowledgeId, int topK);

    /**
     * 按文档ID删除所有向量
     * @param documentId 文档ID
     */
    void deleteVectorsByDocumentId(Long documentId);
}
