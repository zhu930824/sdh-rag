package cn.sdh.backend.service;

import cn.sdh.backend.entity.KnowledgeDocument;

/**
 * 文档服务接口
 */
public interface DocumentService {

    /**
     * 处理文档（解析、分块等）
     * @param documentId 文档ID
     */
    void processDocument(Long documentId);

    /**
     * 对文档进行向量化嵌入
     * @param documentId 文档ID
     */
    void embedDocument(Long documentId);

    /**
     * 重新索引文档
     * @param documentId 文档ID
     */
    void reindexDocument(Long documentId);

    /**
     * 同步所有文档
     * @return 同步的文档数量
     */
    int syncAllDocuments();

    /**
     * 清理旧文档
     * @param days 保留天数
     * @return 清理的文档数量
     */
    int cleanupOldDocuments(int days);

    /**
     * 获取文档
     * @param documentId 文档ID
     * @return 文档实体
     */
    KnowledgeDocument getDocument(Long documentId);

    /**
     * 更新文档
     * @param document 文档实体
     */
    void updateDocument(KnowledgeDocument document);
}