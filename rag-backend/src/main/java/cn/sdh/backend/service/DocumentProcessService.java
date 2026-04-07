package cn.sdh.backend.service;

import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.entity.KnowledgeDocumentRelation;

import java.util.List;

/**
 * 文档处理服务接口
 */
public interface DocumentProcessService {

    /**
     * 处理文档（关联到知识库时调用）
     * @param documentId 文档ID
     * @param knowledgeId 知识库ID
     * @param chunkSize 分块大小
     * @param chunkOverlap 分块重叠
     */
    void processDocumentForKnowledge(Long documentId, Long knowledgeId, Integer chunkSize, Integer chunkOverlap);

    /**
     * 重新处理文档
     * @param documentId 文档ID
     * @param knowledgeId 知识库ID
     */
    void reprocessDocument(Long documentId, Long knowledgeId);

    /**
     * 取消文档与知识库的关联（清理向量）
     * @param documentId 文档ID
     * @param knowledgeId 知识库ID
     */
    void unlinkDocumentFromKnowledge(Long documentId, Long knowledgeId);

    /**
     * 获取处理状态
     * @param documentId 文档ID
     * @param knowledgeId 知识库ID
     * @return 关联记录
     */
    KnowledgeDocumentRelation getProcessStatus(Long documentId, Long knowledgeId);
}
