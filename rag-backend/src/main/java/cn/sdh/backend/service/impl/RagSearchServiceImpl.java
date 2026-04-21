package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RAG检索服务实现
 * 整合混合检索、重排序、查询改写、查询扩展等功能
 */
@Slf4j
@Service
public class RagSearchServiceImpl implements RagSearchService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final VectorStoreService vectorStoreService;
    private final RerankService rerankService;
    private final ChatService chatService;

    private static final String QUERY_EXPANSION_PROMPT = """
        你是一个查询扩展助手。请根据用户的原始查询，生成%d个语义相关但表述不同的查询变体。

        原始查询：%s

        要求：
        1. 保持原始查询的核心意图
        2. 使用同义词、近义词替换关键词
        3. 从不同角度描述同一问题
        4. 每个查询独立成行，不要编号和标点
        5. 只输出扩展查询，每行一个，不要其他内容

        扩展查询：
        """;

    private static final String HYDE_PROMPT = """
        请针对以下问题，写一段可能包含答案的文档内容。
        这段文档将用于信息检索匹配，请确保内容专业、准确、信息丰富。

        问题：%s

        要求：
        1. 内容应当是对该问题的详细回答或解释
        2. 使用专业术语和规范表达
        3. 内容长度控制在200-500字
        4. 直接输出文档内容，不要加任何前缀说明

        文档内容：
        """;

    private static final int RRF_K = 10;

    public RagSearchServiceImpl(
            KnowledgeBaseService knowledgeBaseService,
            VectorStoreService vectorStoreService,
            RerankService rerankService,
            @Lazy ChatService chatService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.vectorStoreService = vectorStoreService;
        this.rerankService = rerankService;
        this.chatService = chatService;
    }


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

            // 2. 查询扩展（如果开启）
            List<String> queries = new ArrayList<>();
            queries.add(searchQuery);

            boolean enableExpansion = knowledgeBase.getEnableQueryExpansion() != null
                    && knowledgeBase.getEnableQueryExpansion();
            int expansionCount = knowledgeBase.getQueryExpansionCount() != null
                    ? knowledgeBase.getQueryExpansionCount() : 3;

            if (enableExpansion) {
                List<String> expandedQueries = expandQuery(searchQuery, expansionCount);
                queries.addAll(expandedQueries);
                result.setExpandedQueries(expandedQueries);
                log.info("查询扩展: 原始=[{}] -> 扩展后={} 个查询", searchQuery, queries.size());
            }

            // 3. HyDE 假设性文档检索（如果开启）
            boolean enableHyde = knowledgeBase.getEnableHyde() != null
                    && knowledgeBase.getEnableHyde();
            String hydeDoc = null;

            if (enableHyde) {
                hydeDoc = generateHypotheticalDocument(searchQuery,
                        knowledgeBase.getHydeModel() != null ? Long.valueOf(knowledgeBase.getHydeModel()) : null);
                if (hydeDoc != null && !hydeDoc.trim().isEmpty()) {
                    result.setHydeDocument(hydeDoc);
                    // 将假设性文档作为额外查询加入检索
                    queries.add(hydeDoc);
                    log.info("HyDE: 生成假设性文档长度={}", hydeDoc.length());
                }
            }

            // 4. 多查询混合检索（相似度阈值在向量检索阶段生效）
            int vectorTopK = knowledgeBase.getVectorTopK() != null ? knowledgeBase.getVectorTopK() : 10;
            int keywordTopK = knowledgeBase.getKeywordTopK() != null ? knowledgeBase.getKeywordTopK() : 10;
            double vectorWeight = knowledgeBase.getVectorWeight() != null ? knowledgeBase.getVectorWeight() : 0.7;
            double keywordWeight = knowledgeBase.getKeywordWeight() != null ? knowledgeBase.getKeywordWeight() : 0.3;
            String embeddingModel = knowledgeBase.getEmbeddingModel();
            Double similarityThreshold = knowledgeBase.getSimilarityThreshold();

            log.info("混合检索参数: queries={}, vectorTopK={}, keywordTopK={}, vectorWeight={}, keywordWeight={}, threshold={}",
                    queries.size(), vectorTopK, keywordTopK, vectorWeight, keywordWeight, similarityThreshold);

            // 对每个查询执行混合检索，然后用 RRF 合并所有结果
            List<Document> documents = multiQuerySearch(
                    queries,
                    knowledgeBase.getId(),
                    vectorTopK,
                    keywordTopK,
                    vectorWeight,
                    keywordWeight,
                    embeddingModel,
                    similarityThreshold
            );

            result.setVectorCount(vectorTopK);
            result.setKeywordCount(keywordTopK);

            if (documents.isEmpty()) {
                log.info("混合检索未找到相关文档");
                result.setDocuments(Collections.emptyList());
                return result;
            }

            // 5. 重排序（使用原始查询，不是扩展查询）
            String rankModel = knowledgeBase.getRankModel();
            if (rankModel != null && !rankModel.isEmpty()) {
                List<String> docTexts = documents.stream()
                        .map(Document::getText)
                        .collect(Collectors.toList());

                // 重排序返回 topK 数量
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

            return result;

        } catch (Exception e) {
            log.error("RAG检索失败: knowledgeId={}, error={}", knowledgeBase.getId(), e.getMessage(), e);
            result.setDocuments(Collections.emptyList());
            return result;
        }
    }

    /**
     * 多查询混合检索
     * 对多个查询分别执行混合检索，然后用 RRF 合并所有结果
     *
     * RRF 分数 = queryWeight / (k + rank)
     * k=10 时，分数区间约 0.09~0.05，区分度明显优于 k=60（0.016~0.014）
     * 原始查询权重 2.0，扩展查询权重 1.0，让原始查询召回的文档排名更靠前
     */
    private List<Document> multiQuerySearch(List<String> queries, Long knowledgeId,
                                            int vectorTopK, int keywordTopK,
                                            double vectorWeight, double keywordWeight,
                                            String embeddingModel, Double minScore) {
        Map<String, Document> docMap = new HashMap<>();
        Map<String, Double> scoreMap = new HashMap<>();

        for (int q = 0; q < queries.size(); q++) {
            try {
                List<Document> queryResults = vectorStoreService.hybridSearch(
                        queries.get(q), knowledgeId, vectorTopK, keywordTopK,
                        vectorWeight, keywordWeight, embeddingModel, minScore
                );

                double queryWeight = (q == 0) ? 2.0 : 1.0;

                for (int i = 0; i < queryResults.size(); i++) {
                    Document doc = queryResults.get(i);
                    String docId = (String) doc.getMetadata().get("id");
                    if (docId == null) {
                        docId = String.valueOf(doc.hashCode());
                    }

                    docMap.putIfAbsent(docId, doc);

                    double rrfScore = queryWeight / (RRF_K + i + 1);
                    scoreMap.merge(docId, rrfScore, Double::sum);
                }
            } catch (Exception e) {
                log.warn("查询执行失败: query={}, error={}", queries.get(q), e.getMessage());
            }
        }

        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> {
                    Document doc = docMap.get(e.getKey());
                    doc.getMetadata().put("hybrid_score", e.getValue());
                    return doc;
                })
                .collect(Collectors.toList());
    }

    @Override
    public String rewriteQuery(String query, List<ChatMessage> chatHistory, Long modelId) {
        if (chatHistory == null || chatHistory.isEmpty()) {
            return query;
        }

        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是一个查询改写助手。请根据对话历史，将用户的最新问题改写为一个独立的、完整的查询。\n\n");
            prompt.append("要求：\n");
            prompt.append("1. 保持用户原有意图\n");
            prompt.append("2. 补充必要的上下文信息\n");
            prompt.append("3. 只输出改写后的查询，不要解释\n\n");

            prompt.append("对话历史：\n");
            for (ChatMessage msg : chatHistory) {
                prompt.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }

            prompt.append("\n用户最新问题: ").append(query);
            prompt.append("\n\n改写后的查询：");

            String rewritten = chatService.chat(prompt.toString(), modelId != null ? modelId.toString() : null);

            if (rewritten != null && !rewritten.trim().isEmpty()) {
                return rewritten.trim();
            }
            return query;

        } catch (Exception e) {
            log.error("查询改写失败: {}", e.getMessage());
            return query;
        }
    }

    @Override
    public List<String> expandQuery(String query, int expansionCount) {
        List<String> expandedQueries = new ArrayList<>();

        try {
            String prompt = String.format(QUERY_EXPANSION_PROMPT, expansionCount, query);
            String response = chatService.chat(prompt, null);

            if (response != null && !response.trim().isEmpty()) {
                // 解析 LLM 返回的多行查询
                String[] lines = response.split("\n");
                for (String line : lines) {
                    String trimmed = line.trim();
                    // 过滤掉编号前缀（如 "1. ", "2. " 等）
                    trimmed = trimmed.replaceAll("^\\d+[.、)\\s]+", "");
                    if (!trimmed.isEmpty() && !trimmed.equals(query)) {
                        expandedQueries.add(trimmed);
                    }
                }

                // 限制数量
                if (expandedQueries.size() > expansionCount) {
                    expandedQueries = expandedQueries.subList(0, expansionCount);
                }
            }

            log.info("查询扩展完成: 原始=[{}], 扩展=[{}]", query, expandedQueries);

        } catch (Exception e) {
            log.error("查询扩展失败: {}", e.getMessage());
        }

        return expandedQueries;
    }

    @Override
    public String generateHypotheticalDocument(String query, Long modelId) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }

        try {
            String prompt = String.format(HYDE_PROMPT, query);
            String hypotheticalDoc = chatService.chat(prompt, modelId != null ? modelId.toString() : null);

            if (hypotheticalDoc != null && !hypotheticalDoc.trim().isEmpty()) {
                log.info("HyDE 生成成功: query=[{}], docLength={}", query, hypotheticalDoc.length());
                return hypotheticalDoc.trim();
            }
            return null;

        } catch (Exception e) {
            log.error("HyDE 生成失败: {}", e.getMessage());
            return null;
        }
    }
}
