package cn.sdh.backend.workflow.executor.impl;

import cn.sdh.backend.service.factory.RerankModelFactory;
import cn.sdh.backend.workflow.executor.NodeExecutor;
import cn.sdh.backend.workflow.model.WorkflowNode;
import com.alibaba.cloud.ai.document.DocumentWithScore;
import com.alibaba.cloud.ai.model.RerankModel;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 重排序节点执行器
 * 对检索结果进行重排序，提高相关性
 */
@Slf4j
@Component
public class RerankNodeExecutor implements NodeExecutor {

    @Autowired
    private RerankModelFactory rerankModelFactory;

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) {
        Map<String, Object> output = new HashMap<>();
        Map<String, Object> data = node.getData();

        if (data == null) {
            output.put("documents", new ArrayList<>());
            output.put("context", "");
            return output;
        }

        // 获取配置
        String model = (String) data.get("model");
        Integer topK = data.get("topK") != null
                ? ((Number) data.get("topK")).intValue() : 5;

        // 获取查询文本
        String query = (String) input.get("query");
        if (query == null || query.isEmpty()) {
            log.warn("重排序节点缺少查询文本");
            output.put("documents", new ArrayList<>());
            output.put("context", "");
            return output;
        }

        // 获取待重排序的文档
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> documents = (List<Map<String, Object>>) input.get("documents");
        if (documents == null || documents.isEmpty()) {
            log.warn("重排序节点没有输入文档");
            output.put("documents", new ArrayList<>());
            output.put("context", "");
            return output;
        }

        log.info("重排序节点配置 - model: {}, topK: {}", model, topK);
        log.info("重排序文档数量: {}", documents.size());

        try {
            // 从工厂获取RerankModel
            RerankModel rerankModel = rerankModelFactory.getModel(model);

            if (rerankModel != null) {
                List<Document> docs = documents.stream()
                        .map(doc -> new Document((String) doc.get("content"),
                                (Map<String, Object>) doc.get("metadata")))
                        .collect(Collectors.toList());

                RerankRequest rerankRequest = new RerankRequest(query, docs);
                RerankResponse response = rerankModel.call(rerankRequest);

                List<DocumentWithScore> results = response.getResults();

                // 按分数排序并取topK
                List<Map<String, Object>> rerankedDocs = results.stream()
                        .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                        .limit(topK)
                        .map(result -> {
                            Map<String, Object> docMap = new HashMap<>();
                            docMap.put("content", result.getOutput().getText());
                            docMap.put("score", result.getScore());
                            docMap.put("metadata", result.getMetadata());
                            return docMap;
                        })
                        .collect(Collectors.toList());

                // 构建上下文
                StringBuilder contextBuilder = new StringBuilder();
                for (int i = 0; i < rerankedDocs.size(); i++) {
                    if (i > 0) {
                        contextBuilder.append("\n\n---\n\n");
                    }
                    contextBuilder.append((String) rerankedDocs.get(i).get("content"));
                }

                output.put("documents", rerankedDocs);
                output.put("context", contextBuilder.toString());
                output.put("query", query);

                log.info("重排序完成，返回 {} 个文档", rerankedDocs.size());

            } else {
                // 如果没有RerankModel，使用简单的相似度计算
                log.info("RerankModel未配置，使用简单排序");
                List<Map<String, Object>> rerankedDocs = simpleRerank(query, documents, topK);

                // 构建上下文
                StringBuilder contextBuilder = new StringBuilder();
                for (int i = 0; i < rerankedDocs.size(); i++) {
                    if (i > 0) {
                        contextBuilder.append("\n\n---\n\n");
                    }
                    contextBuilder.append((String) rerankedDocs.get(i).get("content"));
                }

                output.put("documents", rerankedDocs);
                output.put("context", contextBuilder.toString());
                output.put("query", query);
            }

        } catch (Exception e) {
            log.error("重排序失败", e);
            // 失败时返回原始文档
            output.put("documents", documents.stream().limit(topK).collect(Collectors.toList()));
            output.put("context", documents.stream()
                    .limit(topK)
                    .map(d -> (String) d.get("content"))
                    .collect(Collectors.joining("\n\n---\n\n")));
            output.put("error", e.getMessage());
        }

        return output;
    }

    /**
     * 简单的重排序方法（基于关键词匹配）
     */
    private List<Map<String, Object>> simpleRerank(String query, List<Map<String, Object>> documents, int topK) {
        String[] queryWords = query.toLowerCase().split("\\s+");

        return documents.stream()
                .map(doc -> {
                    String content = ((String) doc.get("content")).toLowerCase();
                    double score = 0;
                    for (String word : queryWords) {
                        if (content.contains(word)) {
                            score += 1.0;
                        }
                    }
                    // 添加原始分数
                    Object originalScore = doc.get("score");
                    if (originalScore != null) {
                        score += ((Number) originalScore).doubleValue();
                    }

                    Map<String, Object> newDoc = new HashMap<>(doc);
                    newDoc.put("rerankScore", score);
                    return newDoc;
                })
                .sorted((a, b) -> Double.compare((Double) b.get("rerankScore"), (Double) a.get("rerankScore")))
                .limit(topK)
                .collect(Collectors.toList());
    }

    @Override
    public String getSupportedNodeType() {
        return "rerank";
    }
}
