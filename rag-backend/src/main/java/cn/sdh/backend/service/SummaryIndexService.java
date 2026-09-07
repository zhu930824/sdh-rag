package cn.sdh.backend.service;

import java.util.List;
import java.util.Map;

/**
 * 摘要索引服务 — 管理文档摘要的 ES 索引和粗筛检索
 */
public interface SummaryIndexService {

    String SUMMARY_INDEX_PREFIX = "sdh-rag-summary-";
    String COLD_SUMMARY_INDEX_PREFIX = "sdh-rag-summary-cold-";

    void indexDocumentSummary(Long documentId, Long knowledgeId, String title,
                              String content, String embeddingModel);

    void indexDocumentSummary(Long documentId, Long knowledgeId, String title,
                              String content, String embeddingModel,
                              Map<String, Object> documentMetadata);

    Map<Long, Double> coarseRetrieval(String query, Long knowledgeId, int topK,
                                       String embeddingModel,
                                       Map<String, Object> metadataFilters);

    void deleteDocumentSummary(Long documentId, Long knowledgeId);

    void deleteByKnowledgeId(Long knowledgeId);
}
