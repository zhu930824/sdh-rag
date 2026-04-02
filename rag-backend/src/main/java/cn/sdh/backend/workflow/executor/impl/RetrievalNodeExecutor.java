package cn.sdh.backend.workflow.executor.impl;

import cn.sdh.backend.workflow.executor.NodeExecutor;
import cn.sdh.backend.workflow.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 检索节点执行器
 */
@Slf4j
@Component
public class RetrievalNodeExecutor implements NodeExecutor {

    @Autowired(required = false)
    private VectorStore vectorStore;

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) {
        Map<String, Object> output = new HashMap<>();
        Map<String, Object> data = node.getData();

        if (data == null) {
            output.put("context", "");
            output.put("documents", new ArrayList<>());
            return output;
        }

        // 获取配置
        Long knowledgeBaseId = data.get("knowledgeBaseId") != null
            ? ((Number) data.get("knowledgeBaseId")).longValue() : null;
        Integer topK = data.get("topK") != null
            ? ((Number) data.get("topK")).intValue() : 5;
        Double scoreThreshold = data.get("scoreThreshold") != null
            ? ((Number) data.get("scoreThreshold")).doubleValue() : 0.7;

        // 获取查询文本
        String query = (String) input.get("query");
        if (query == null) {
            query = (String) input.get("input");
        }

        if (query == null || query.isEmpty()) {
            log.warn("检索节点缺少查询文本");
            output.put("context", "");
            output.put("documents", new ArrayList<>());
            return output;
        }

        log.info("检索节点配置 - knowledgeBaseId: {}, topK: {}, scoreThreshold: {}", knowledgeBaseId, topK, scoreThreshold);
        log.info("检索查询: {}", query);

        try {
            if (vectorStore == null) {
                log.warn("VectorStore 未注入，返回模拟结果");
                output.put("context", "【模拟检索结果】VectorStore 未配置。");
                output.put("documents", List.of(Map.of(
                    "content", "模拟文档内容",
                    "score", 0.95,
                    "metadata", Map.of()
                )));
                return output;
            }

            // 执行向量检索
            SearchRequest.Builder builder = SearchRequest.builder().
                    query(query)
                    .topK(topK)
                    .similarityThreshold(scoreThreshold);

            // 如果指定了知识库，添加过滤条件
            if (knowledgeBaseId != null) {
                log.info("添加知识库过滤条件: knowledgeBaseId == {}", knowledgeBaseId);
                builder.filterExpression("knowledgeBaseId == " + knowledgeBaseId);
            }

            List<Document> documents = vectorStore.similaritySearch(builder.build());

            log.info("检索到 {} 个文档", documents.size());

            // 构建输出
            List<Map<String, Object>> docList = new ArrayList<>();
            StringBuilder contextBuilder = new StringBuilder();

            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                Map<String, Object> docMap = new HashMap<>();
                docMap.put("content", doc.getText());
                docMap.put("score", doc.getMetadata().get("score"));
                docMap.put("metadata", doc.getMetadata());
                docList.add(docMap);

                if (i > 0) {
                    contextBuilder.append("\n\n---\n\n");
                }
                contextBuilder.append(doc.getText());
            }

            output.put("documents", docList);
            output.put("context", contextBuilder.toString());
            output.put("query", query);

        } catch (Exception e) {
            log.error("检索失败", e);
            output.put("context", "检索失败: " + e.getMessage());
            output.put("documents", new ArrayList<>());
            output.put("error", e.getMessage());
        }

        return output;
    }

    @Override
    public String getSupportedNodeType() {
        return "retrieval";
    }
}
