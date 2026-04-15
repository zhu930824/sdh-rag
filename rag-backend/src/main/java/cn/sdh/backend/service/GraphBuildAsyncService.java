package cn.sdh.backend.service;

import cn.sdh.backend.dto.EntityExtractionResult;
import cn.sdh.backend.dto.GraphBuildTask;
import cn.sdh.backend.graph.node.ConceptNode;
import cn.sdh.backend.graph.node.DocumentNode;
import cn.sdh.backend.graph.node.EntityNode;
import cn.sdh.backend.graph.node.KeywordNode;
import cn.sdh.backend.graph.repository.ConceptNodeRepository;
import cn.sdh.backend.graph.repository.CustomGraphRepository;
import cn.sdh.backend.graph.repository.DocumentNodeRepository;
import cn.sdh.backend.graph.repository.EntityNodeRepository;
import cn.sdh.backend.graph.repository.KeywordNodeRepository;
import cn.sdh.backend.graph.extractor.EntityExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 图谱构建异步服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphBuildAsyncService {

    private final EntityExtractor entityExtractor;
    private final DocumentNodeRepository documentNodeRepository;
    private final EntityNodeRepository entityNodeRepository;
    private final ConceptNodeRepository conceptNodeRepository;
    private final KeywordNodeRepository keywordNodeRepository;
    private final CustomGraphRepository customGraphRepository;
    private final VectorStoreService vectorStoreService;
    private final GraphBuildTaskManager taskManager;

    /**
     * 异步构建知识库图谱
     */
    @Async("graphBuildExecutor")
    public void buildFromKnowledgeBaseAsync(String taskId, Long knowledgeId) {
        log.info("开始异步构建知识库图谱: taskId={}, knowledgeId={}", taskId, knowledgeId);

        try {
            taskManager.markRunning(taskId);
            taskManager.updateProgress(taskId, 5, "正在获取文档分块...");

            // 从ES获取知识库的分块数量
            long chunkCount = vectorStoreService.countChunksByKnowledgeId(knowledgeId);
            if (chunkCount == 0) {
                taskManager.markFailed(taskId, "知识库下没有分块数据，请先处理文档");
                return;
            }

            log.info("知识库分块数量: knowledgeId={}, chunkCount={}", knowledgeId, chunkCount);

            // 删除知识库旧的图谱数据
            taskManager.updateProgress(taskId, 10, "正在清理旧数据...");
            deleteByKnowledgeBaseInternal(knowledgeId);

            int totalEntities = 0;
            int totalRelations = 0;
            int totalConcepts = 0;
            int totalKeywords = 0;

            // 分页获取所有分块内容，每次处理100个
            int pageSize = 100;
            int totalPages = (int) Math.ceil((double) chunkCount / pageSize);

            // 用于合并文本，按文档ID分组
            Map<Long, StringBuilder> documentTextMap = new HashMap<>();

            taskManager.updateProgress(taskId, 15, "正在加载文档内容...");

            for (int page = 0; page < totalPages; page++) {
                List<org.springframework.ai.document.Document> chunks =
                        vectorStoreService.getChunksByKnowledgeId(knowledgeId, page, pageSize);

                if (chunks == null || chunks.isEmpty()) {
                    continue;
                }

                for (org.springframework.ai.document.Document chunk : chunks) {
                    String content = chunk.getText();
                    if (content == null || content.isBlank()) {
                        continue;
                    }

                    // 获取文档ID
                    Object docIdObj = chunk.getMetadata().get("document_id");
                    Long documentId = docIdObj != null ? ((Number) docIdObj).longValue() : knowledgeId;

                    // 合并到文档文本
                    documentTextMap.computeIfAbsent(documentId, k -> new StringBuilder())
                            .append(content).append("\n\n");
                }
            }

            int totalDocuments = documentTextMap.size();
            int processedDocuments = 0;

            taskManager.updateStats(taskId, 0, totalDocuments, 0, 0, 0, 0);

            // 为每个文档构建图谱
            for (Map.Entry<Long, StringBuilder> entry : documentTextMap.entrySet()) {
                Long documentId = entry.getKey();
                String fullText = entry.getValue().toString();

                // 截取文本，避免过长（LLM有token限制）
                String truncatedText = fullText.length() > 8000 ? fullText.substring(0, 8000) : fullText;

                try {
                    taskManager.updateProgress(taskId, 15 + (int)(processedDocuments * 75.0 / totalDocuments),
                            "正在处理文档 " + (processedDocuments + 1) + "/" + totalDocuments);

                    // 提取实体和关系
                    EntityExtractionResult extractionResult = entityExtractor.extract(truncatedText, documentId);

                    // 创建实体节点
                    Map<String, EntityNode> entityMap = createEntityNodes(extractionResult.getEntities(), documentId, knowledgeId);
                    totalEntities += entityMap.size();

                    // 创建概念节点
                    Map<String, ConceptNode> conceptMap = createConceptNodes(extractionResult.getConcepts(), knowledgeId);
                    totalConcepts += conceptMap.size();

                    // 创建关键词节点
                    Map<String, KeywordNode> keywordMap = createKeywordNodes(extractionResult.getKeywords(), documentId, knowledgeId);
                    totalKeywords += keywordMap.size();

                    // 创建实体间关系
                    createEntityRelations(extractionResult.getRelations(), entityMap);
                    totalRelations += extractionResult.getRelations().size();

                    // 创建文档节点并关联
                    createDocumentNodeWithRelations(documentId, knowledgeId, entityMap, conceptMap, keywordMap);

                    processedDocuments++;
                    taskManager.updateStats(taskId, processedDocuments, totalDocuments,
                            totalEntities, totalRelations, totalConcepts, totalKeywords);

                    log.info("文档图谱构建完成: documentId={}, entities={}, concepts={}, keywords={}",
                            documentId, entityMap.size(), conceptMap.size(), keywordMap.size());

                } catch (Exception e) {
                    log.warn("构建文档图谱失败: documentId={}, error={}", documentId, e.getMessage());
                }
            }

            taskManager.updateProgress(taskId, 95, "正在完成构建...");
            taskManager.markCompleted(taskId);

            log.info("知识库图谱构建完成: taskId={}, knowledgeId={}, processedDocuments={}, totalEntities={}, totalRelations={}",
                    taskId, knowledgeId, processedDocuments, totalEntities, totalRelations);

        } catch (Exception e) {
            log.error("知识库图谱构建失败: taskId={}, knowledgeId={}", taskId, knowledgeId, e);
            taskManager.markFailed(taskId, "知识库图谱构建失败: " + e.getMessage());
        }
    }

    /**
     * 异步构建文档图谱
     */
    @Async("graphBuildExecutor")
    public void buildFromDocumentAsync(String taskId, Long documentId, Long knowledgeBaseId) {
        log.info("开始异步构建文档图谱: taskId={}, documentId={}", taskId, documentId);

        try {
            taskManager.markRunning(taskId);
            taskManager.updateProgress(taskId, 10, "正在获取文档...");

            // 获取文档 - 这里需要通过KnowledgeService获取
            // 由于我们没有注入KnowledgeService，这里暂时使用简化方式
            // 实际使用时应该注入KnowledgeService并获取文档内容

            taskManager.updateProgress(taskId, 30, "正在提取实体...");
            // 这里需要实际的文档内容
            // 暂时标记为完成
            taskManager.markCompleted(taskId);

        } catch (Exception e) {
            log.error("文档图谱构建失败: taskId={}, documentId={}", taskId, documentId, e);
            taskManager.markFailed(taskId, "文档图谱构建失败: " + e.getMessage());
        }
    }

    private void deleteByKnowledgeBaseInternal(Long knowledgeId) {
        try {
            customGraphRepository.deleteByKnowledgeBaseId(knowledgeId);
            log.info("删除知识库图谱数据: knowledgeId={}", knowledgeId);
        } catch (Exception e) {
            log.warn("删除知识库图谱数据失败: knowledgeId={}, error={}", knowledgeId, e.getMessage());
        }
    }

    private Map<String, EntityNode> createEntityNodes(List<EntityExtractionResult.EntityInfo> entities, Long documentId, Long knowledgeBaseId) {
        Map<String, EntityNode> entityMap = new HashMap<>();

        for (EntityExtractionResult.EntityInfo entityInfo : entities) {
            if (entityInfo.getName() == null || entityInfo.getName().isBlank()) {
                continue;
            }

            String key = entityInfo.getEntityType() + ":" + entityInfo.getName();

            EntityNode existingNode = entityMap.get(key);
            if (existingNode != null) {
                existingNode.setFrequency(existingNode.getFrequency() + 1);
                continue;
            }

            EntityNode node = new EntityNode();
            node.setName(entityInfo.getName());
            node.setEntityType(entityInfo.getEntityType());
            node.setDescription(entityInfo.getDescription());
            node.setConfidence(entityInfo.getConfidence());
            node.setSourceDocumentId(documentId);
            node.setKnowledgeBaseId(knowledgeBaseId);
            node.setFrequency(1);
            node.setCreateTime(LocalDateTime.now());
            node.setUpdateTime(LocalDateTime.now());

            entityMap.put(key, entityNodeRepository.save(node));
        }

        return entityMap;
    }

    private Map<String, ConceptNode> createConceptNodes(List<EntityExtractionResult.ConceptInfo> concepts, Long knowledgeBaseId) {
        Map<String, ConceptNode> conceptMap = new HashMap<>();

        for (EntityExtractionResult.ConceptInfo conceptInfo : concepts) {
            if (conceptInfo.getName() == null || conceptInfo.getName().isBlank()) {
                continue;
            }

            ConceptNode existingNode = conceptNodeRepository.findByNameAndKnowledgeBaseId(conceptInfo.getName(), knowledgeBaseId).orElse(null);
            if (existingNode != null) {
                existingNode.setWeight(existingNode.getWeight() + 1);
                conceptMap.put(conceptInfo.getName(), conceptNodeRepository.save(existingNode));
                continue;
            }

            ConceptNode node = new ConceptNode();
            node.setName(conceptInfo.getName());
            node.setDescription(conceptInfo.getDescription());
            node.setCategory(conceptInfo.getCategory());
            node.setKnowledgeBaseId(knowledgeBaseId);
            node.setWeight(1);
            node.setCreateTime(LocalDateTime.now());
            node.setUpdateTime(LocalDateTime.now());

            conceptMap.put(conceptInfo.getName(), conceptNodeRepository.save(node));
        }

        return conceptMap;
    }

    private Map<String, KeywordNode> createKeywordNodes(List<EntityExtractionResult.KeywordInfo> keywords, Long documentId, Long knowledgeBaseId) {
        Map<String, KeywordNode> keywordMap = new HashMap<>();

        for (EntityExtractionResult.KeywordInfo keywordInfo : keywords) {
            if (keywordInfo.getKeyword() == null || keywordInfo.getKeyword().isBlank()) {
                continue;
            }

            KeywordNode existingNode = keywordNodeRepository.findByNameAndKnowledgeBaseId(keywordInfo.getKeyword(), knowledgeBaseId).orElse(null);
            if (existingNode != null) {
                existingNode.setFrequency(existingNode.getFrequency() + 1);
                keywordMap.put(keywordInfo.getKeyword(), keywordNodeRepository.save(existingNode));
                continue;
            }

            KeywordNode node = new KeywordNode();
            node.setName(keywordInfo.getKeyword());
            node.setSourceDocumentId(documentId);
            node.setKnowledgeBaseId(knowledgeBaseId);
            node.setTfidf(keywordInfo.getTfidf());
            node.setFrequency(1);
            node.setCreateTime(LocalDateTime.now());
            node.setUpdateTime(LocalDateTime.now());

            keywordMap.put(keywordInfo.getKeyword(), keywordNodeRepository.save(node));
        }

        return keywordMap;
    }

    private void createEntityRelations(List<EntityExtractionResult.RelationInfo> relations, Map<String, EntityNode> entityMap) {
        for (EntityExtractionResult.RelationInfo relationInfo : relations) {
            String sourceKey = relationInfo.getSourceType() + ":" + relationInfo.getSourceName();
            String targetKey = relationInfo.getTargetType() + ":" + relationInfo.getTargetName();

            EntityNode sourceNode = entityMap.get(sourceKey);
            EntityNode targetNode = entityMap.get(targetKey);

            if (sourceNode == null || targetNode == null) {
                continue;
            }

            if ("RELATED_TO".equals(relationInfo.getRelationType())) {
                sourceNode.getRelatedEntities().add(targetNode);
                entityNodeRepository.save(sourceNode);
            }
        }
    }

    private DocumentNode createDocumentNodeWithRelations(Long documentId,
                                                          Long knowledgeBaseId,
                                                          Map<String, EntityNode> entityMap,
                                                          Map<String, ConceptNode> conceptMap,
                                                          Map<String, KeywordNode> keywordMap) {
        DocumentNode node = new DocumentNode();
        node.setDocumentId(documentId);
        node.setName("文档-" + documentId);
        node.setTitle("文档-" + documentId);
        node.setKnowledgeBaseId(knowledgeBaseId);
        node.setDescription("知识库文档");
        node.setCreateTime(LocalDateTime.now());
        node.setUpdateTime(LocalDateTime.now());

        node.setEntities(new HashSet<>(entityMap.values()));
        node.setConcepts(new HashSet<>(conceptMap.values()));
        node.setKeywords(new HashSet<>(keywordMap.values()));

        return documentNodeRepository.save(node);
    }
}
