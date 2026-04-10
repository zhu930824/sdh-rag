package cn.sdh.backend.rag;

import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.service.VectorStoreService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import java.util.List;

/**
 * 混合检索文档检索器
 * 支持向量检索 + 关键字检索的混合模式
 */
@Slf4j
@Getter
@RequiredArgsConstructor
public class HybridDocumentRetriever implements DocumentRetriever {

    private final VectorStoreService vectorStoreService;
    private final KnowledgeBase knowledgeBase;

    @Override
    public List<Document> retrieve(Query query) {
        if (knowledgeBase == null) {
            log.warn("知识库配置为空，无法检索");
            return List.of();
        }

        String queryText = query.text();
        Long knowledgeId = knowledgeBase.getId();

        // 获取检索参数，使用默认值
        int vectorTopK = knowledgeBase.getVectorTopK() != null ? knowledgeBase.getVectorTopK() : 10;
        int keywordTopK = knowledgeBase.getKeywordTopK() != null ? knowledgeBase.getKeywordTopK() : 10;
        double vectorWeight = knowledgeBase.getVectorWeight() != null ? knowledgeBase.getVectorWeight() : 0.7;
        double keywordWeight = knowledgeBase.getKeywordWeight() != null ? knowledgeBase.getKeywordWeight() : 0.3;
        String embeddingModel = knowledgeBase.getEmbeddingModel();

        log.info("混合检索: knowledgeId={}, query={}, vectorTopK={}, keywordTopK={}, vectorWeight={}, keywordWeight={}",
                knowledgeId, queryText, vectorTopK, keywordTopK, vectorWeight, keywordWeight);

        // 调用混合检索服务
        List<Document> documents = vectorStoreService.hybridSearch(
                queryText,
                knowledgeId,
                vectorTopK,
                keywordTopK,
                vectorWeight,
                keywordWeight,
                embeddingModel
        );

        log.info("混合检索完成: 召回文档数={}", documents.size());
        return documents;
    }
}
