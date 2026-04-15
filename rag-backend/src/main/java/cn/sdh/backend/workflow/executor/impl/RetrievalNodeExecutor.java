package cn.sdh.backend.workflow.executor.impl;

import cn.sdh.backend.service.VectorStoreService;
import cn.sdh.backend.workflow.executor.NodeExecutor;
import cn.sdh.backend.workflow.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索节点执行器
 */
@Slf4j
@Component
public class RetrievalNodeExecutor implements NodeExecutor {

    @Autowired
    private VectorStoreService vectorStoreService;

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
            if (knowledgeBaseId == null) {
                log.warn("未指定知识库ID，返回空结果");
                output.put("context", "");
                output.put("documents", new ArrayList<>());
                return output;
            }

            // 执行向量检索（使用VectorStoreService，自动处理知识库过滤）
            List<Document> documents = vectorStoreService.similaritySearch(query, knowledgeBaseId, topK);

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
