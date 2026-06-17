package cn.sdh.backend.service;

/**
 * 文档去重服务
 */
public interface DocumentDedupService {

    /**
     * MD5 快速判重
     * @param knowledgeId 知识库ID
     * @param contentMd5 内容MD5哈希
     * @return 已存在的文档ID，null 表示不重复
     */
    Long checkMd5Duplicate(Long knowledgeId, String contentMd5);

    /**
     * MinHash 近似查重
     * @param knowledgeId 知识库ID
     * @param content 文档内容
     * @param threshold Jaccard 相似度阈值（0-1），默认 0.85
     * @return 最相似的文档ID和相似度，null 表示无相似文档
     */
    SimilarDocResult checkMinHashSimilarity(Long knowledgeId, String content, double threshold);

    /**
     * 保存文档哈希
     */
    void saveDocumentHash(Long documentId, Long knowledgeId, String contentMd5, String content);

    /**
     * MinHash 近似查重结果
     */
    record SimilarDocResult(Long documentId, Double similarity) {}
}
