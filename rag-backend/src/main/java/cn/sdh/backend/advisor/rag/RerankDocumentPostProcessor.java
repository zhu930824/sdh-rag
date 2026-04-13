package cn.sdh.backend.advisor.rag;

import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.service.RerankService;
import cn.sdh.backend.service.RerankService.RerankResult;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 重排序文档后处理器
 * 对检索到的文档进行重排序，提高相关性
 */
@Slf4j
@Getter
@RequiredArgsConstructor
public class RerankDocumentPostProcessor implements DocumentPostProcessor {

    private final RerankService rerankService;
    private final KnowledgeBase knowledgeBase;

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        // 如果没有配置重排序模型，直接返回原文档
        if (knowledgeBase == null || knowledgeBase.getRankModel() == null || knowledgeBase.getRankModel().isEmpty()) {
            log.debug("未配置重排序模型，跳过重排序");
            return documents;
        }

        if (documents == null || documents.isEmpty()) {
            return documents;
        }

        String rankModel = knowledgeBase.getRankModel();
        String queryText = query.text();

        log.info("开始重排序: model={}, 文档数={}", rankModel, documents.size());

        // 提取文档文本
        List<String> docTexts = documents.stream()
                .map(Document::getText)
                .collect(Collectors.toList());

        // 计算重排序 TopK（取 vectorTopK 和 keywordTopK 的最大值）
        int rerankTopK = Math.max(
                knowledgeBase.getVectorTopK() != null ? knowledgeBase.getVectorTopK() : 10,
                knowledgeBase.getKeywordTopK() != null ? knowledgeBase.getKeywordTopK() : 10
        );

        // 调用重排序服务
        List<RerankResult> rerankResults = rerankService.rerank(queryText, docTexts, rerankTopK, rankModel);

        // 按重排序结果重新组织文档
        List<Document> rerankedDocs = new ArrayList<>();
        for (RerankResult rr : rerankResults) {
            if (rr.getIndex() < documents.size()) {
                Document doc = documents.get(rr.getIndex());
                // 添加重排序分数到元数据
                doc.getMetadata().put("rerank_score", rr.getScore());
                rerankedDocs.add(doc);
            }
        }

        log.info("重排序完成: 输入文档数={}, 输出文档数={}", documents.size(), rerankedDocs.size());
        return rerankedDocs;
    }
}
