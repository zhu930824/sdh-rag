package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG检索服务实现
 * 整合混合检索、重排序、查询改写等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchServiceImpl implements RagSearchService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final VectorStoreService vectorStoreService;
    private final RerankService rerankService;
    private final AiChatService aiChatService;

    @Override
    public RagSearchResult search(String query, Long knowledgeId, List<ChatMessage> chatHistory) {
        KnowledgeBase knowledgeBase = knowledgeBaseService.getKnowledgeBaseById(knowledgeId);
        if (knowledgeBase == null) {
            log.warn("知识库不存在: {}", knowledgeId);
            RagSearchResult emptyResult = new RagSearchResult();
            emptyResult.setDocuments(Collections.emptyList());
            return emptyResult;
        }
        return search(query, knowledgeBase, chatHistory);
    }

    @Override
    public RagSearchResult search(String query, KnowledgeBase knowledgeBase, List<ChatMessage> chatHistory) {
        RagSearchResult result = new RagSearchResult();
        String searchQuery = query;

        try {
            // 1. 查询改写（如果开启了多轮对话改写）
            if (knowledgeBase.getEnableRewrite() != null && knowledgeBase.getEnableRewrite()
                    && chatHistory != null && !chatHistory.isEmpty()) {
                searchQuery = rewriteQuery(query, chatHistory, null);
                result.setRewrittenQuery(searchQuery);
                log.info("查询改写: 原始=[{}] -> 改写=[{}]", query, searchQuery);
            }

            // 2. 混合检索
            int vectorTopK = knowledgeBase.getVectorTopK() != null ? knowledgeBase.getVectorTopK() : 10;
            int keywordTopK = knowledgeBase.getKeywordTopK() != null ? knowledgeBase.getKeywordTopK() : 10;
            double vectorWeight = knowledgeBase.getVectorWeight() != null ? knowledgeBase.getVectorWeight() : 0.7;
            double keywordWeight = knowledgeBase.getKeywordWeight() != null ? knowledgeBase.getKeywordWeight() : 0.3;
            String embeddingModel = knowledgeBase.getEmbeddingModel();

            log.info("混合检索参数: vectorTopK={}, keywordTopK={}, vectorWeight={}, keywordWeight={}",
                    vectorTopK, keywordTopK, vectorWeight, keywordWeight);

            List<Document> documents = vectorStoreService.hybridSearch(
                    searchQuery,
                    knowledgeBase.getId(),
                    vectorTopK,
                    keywordTopK,
                    vectorWeight,
                    keywordWeight,
                    embeddingModel
            );

            result.setVectorCount(vectorTopK);
            result.setKeywordCount(keywordTopK);

            if (documents.isEmpty()) {
                log.info("混合检索未找到相关文档");
                result.setDocuments(Collections.emptyList());
                return result;
            }

            // 3. 重排序
            String rankModel = knowledgeBase.getRankModel();
            if (rankModel != null && !rankModel.isEmpty()) {
                List<String> docTexts = documents.stream()
                        .map(Document::getText)
                        .collect(Collectors.toList());

                // 重排序返回 topK 数量（取 vectorTopK 和 keywordTopK 的最大值作为最终数量）
                int rerankTopK = Math.max(vectorTopK, keywordTopK);
                List<RerankService.RerankResult> rerankResults = rerankService.rerank(
                        searchQuery, docTexts, rerankTopK, rankModel);

                // 按重排序结果重新组织文档
                List<Document> rerankedDocs = new ArrayList<>();
                for (RerankService.RerankResult rr : rerankResults) {
                    if (rr.getIndex() < documents.size()) {
                        Document doc = documents.get(rr.getIndex());
                        doc.getMetadata().put("rerank_score", rr.getScore());
                        rerankedDocs.add(doc);
                    }
                }

                result.setDocuments(rerankedDocs);
                result.setRerankedCount(rerankedDocs.size());
                log.info("重排序完成: 输入文档数={}, 输出文档数={}", documents.size(), rerankedDocs.size());
            } else {
                result.setDocuments(documents);
                result.setRerankedCount(documents.size());
            }

            // 4. 相似度阈值过滤
            Double similarityThreshold = knowledgeBase.getSimilarityThreshold();
            if (similarityThreshold != null && similarityThreshold > 0) {
                List<Document> filteredDocs = result.getDocuments().stream()
                        .filter(doc -> {
                            Object rerankScore = doc.getMetadata().get("rerank_score");
                            Object hybridScore = doc.getMetadata().get("hybrid_score");
                            Object originalScore = doc.getMetadata().get("score");

                            // 优先使用重排序分数，其次混合分数，最后原始分数
                            if (rerankScore != null) {
                                return ((Number) rerankScore).doubleValue() >= similarityThreshold;
                            } else if (hybridScore != null) {
                                // 混合分数是 RRF 分数，需要转换为相似度
                                return true; // RRF 分数不好直接比较阈值，暂时不过滤
                            } else if (originalScore != null) {
                                return ((Number) originalScore).doubleValue() >= similarityThreshold;
                            }
                            return true;
                        })
                        .collect(Collectors.toList());

                result.setDocuments(filteredDocs);
                log.info("相似度阈值过滤: threshold={}, 过滤前={}, 过滤后={}",
                        similarityThreshold, result.getRerankedCount(), filteredDocs.size());
            }

            return result;

        } catch (Exception e) {
            log.error("RAG检索失败: knowledgeId={}, error={}", knowledgeBase.getId(), e.getMessage(), e);
            result.setDocuments(Collections.emptyList());
            return result;
        }
    }

    @Override
    public String rewriteQuery(String query, List<ChatMessage> chatHistory, Long modelId) {
        if (chatHistory == null || chatHistory.isEmpty()) {
            return query;
        }

        try {
            // 构建改写提示词
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是一个查询改写助手。请根据对话历史，将用户的最新问题改写为一个独立的、完整的查询。\n\n");
            prompt.append("要求：\n");
            prompt.append("1. 保持用户原有意图\n");
            prompt.append("2. 补充必要的上下文信息\n");
            prompt.append("3. 只输出改写后的查询，不要解释\n\n");

            prompt.append("对话历史：\n");
            for (int i = 0; i < chatHistory.size(); i++) {
                ChatMessage msg = chatHistory.get(i);
                prompt.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }

            prompt.append("\n用户最新问题: ").append(query);
            prompt.append("\n\n改写后的查询：");

            // 调用 LLM 进行改写
            String rewritten = aiChatService.chat(prompt.toString(), modelId != null ? modelId.toString() : null);

            if (rewritten != null && !rewritten.trim().isEmpty()) {
                return rewritten.trim();
            }
            return query;

        } catch (Exception e) {
            log.error("查询改写失败: {}", e.getMessage());
            return query;
        }
    }
}