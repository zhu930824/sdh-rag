package cn.sdh.backend.service.impl;

import cn.sdh.backend.dto.EntityExtractionResult;
import cn.sdh.backend.dto.GraphBuildResponse;
import cn.sdh.backend.dto.GraphBuildTask;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.graph.extractor.EntityExtractor;
import cn.sdh.backend.graph.node.ConceptNode;
import cn.sdh.backend.graph.node.DocumentNode;
import cn.sdh.backend.graph.node.EntityNode;
import cn.sdh.backend.graph.node.KeywordNode;
import cn.sdh.backend.graph.repository.ConceptNodeRepository;
import cn.sdh.backend.graph.repository.CustomGraphRepository;
import cn.sdh.backend.graph.repository.DocumentNodeRepository;
import cn.sdh.backend.graph.repository.EntityNodeRepository;
import cn.sdh.backend.graph.repository.KeywordNodeRepository;
import cn.sdh.backend.service.GraphBuildService;
import cn.sdh.backend.service.GraphBuildAsyncService;
import cn.sdh.backend.service.GraphBuildTaskManager;
import cn.sdh.backend.service.KnowledgeBaseService;
import cn.sdh.backend.service.KnowledgeService;
import cn.sdh.backend.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 图谱构建服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphBuildServiceImpl implements GraphBuildService {

    private final EntityExtractor entityExtractor;
    private final DocumentNodeRepository documentNodeRepository;
    private final EntityNodeRepository entityNodeRepository;
    private final ConceptNodeRepository conceptNodeRepository;
    private final KeywordNodeRepository keywordNodeRepository;
    private final CustomGraphRepository customGraphRepository;
    private final KnowledgeService knowledgeService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final VectorStoreService vectorStoreService;
    private final GraphBuildAsyncService graphBuildAsyncService;
    private final GraphBuildTaskManager taskManager;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GraphBuildResponse buildFromDocument(Long documentId) {
        // 单文档构建不关联知识库，使用null作为knowledgeBaseId
        return buildFromDocument(documentId, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public GraphBuildResponse buildFromDocument(Long documentId, Long knowledgeBaseId) {
        try {
            // 获取文档
            KnowledgeDocument document = knowledgeService.getDocumentById(documentId);
            if (document == null) {
                return GraphBuildResponse.fail(documentId, "文档不存在");
            }

            if (document.getContent() == null || document.getContent().isBlank()) {
                return GraphBuildResponse.fail(documentId, "文档内容为空");
            }

            // 删除旧的图谱数据
            deleteByDocument(documentId);

            // 提取实体和关系
            EntityExtractionResult extractionResult = entityExtractor.extract(document.getContent(), documentId);

            // 创建文档节点
            DocumentNode documentNode = createDocumentNode(document, knowledgeBaseId);

            // 创建实体节点
            Map<String, EntityNode> entityMap = createEntityNodes(extractionResult.getEntities(), documentId, knowledgeBaseId);

            // 创建概念节点
            Map<String, ConceptNode> conceptMap = createConceptNodes(extractionResult.getConcepts(), knowledgeBaseId);

            // 创建关键词节点
            Map<String, KeywordNode> keywordMap = createKeywordNodes(extractionResult.getKeywords(), documentId, knowledgeBaseId);

            // 建立文档与实体/概念/关键词的关系
            documentNode.setEntities(new HashSet<>(entityMap.values()));
            documentNode.setConcepts(new HashSet<>(conceptMap.values()));
            documentNode.setKeywords(new HashSet<>(keywordMap.values()));
            documentNodeRepository.save(documentNode);

            // 创建实体间关系
            createEntityRelations(extractionResult.getRelations(), entityMap);

            log.info("图谱构建完成: documentId={}, knowledgeBaseId={}, entities={}, relations={}, concepts={}, keywords={}",
                    documentId, knowledgeBaseId, entityMap.size(), extractionResult.getRelations().size(),
                    conceptMap.size(), keywordMap.size());

            return GraphBuildResponse.success(documentId,
                    entityMap.size(),
                    extractionResult.getRelations().size(),
                    conceptMap.size(),
                    keywordMap.size());

        } catch (Exception e) {
            log.error("图谱构建失败: documentId={}", documentId, e);
            return GraphBuildResponse.fail(documentId, "图谱构建失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GraphBuildResponse rebuildFromDocument(Long documentId) {
        return buildFromDocument(documentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByDocument(Long documentId) {
        documentNodeRepository.deleteByDocumentId(documentId);
    }

    @Override
    public void batchBuild(Long[] documentIds) {
        for (Long documentId : documentIds) {
            try {
                buildFromDocument(documentId);
            } catch (Exception e) {
                log.error("批量构建图谱失败: documentId={}", documentId, e);
            }
        }
    }

    // ==================== 异步构建方法 ====================

    @Override
    public GraphBuildTask buildFromKnowledgeBaseAsync(Long knowledgeId) {
        // 创建任务
        GraphBuildTask task = taskManager.createTask(knowledgeId, null, GraphBuildTask.TaskType.KNOWLEDGE_BASE);

        // 异步执行构建
        graphBuildAsyncService.buildFromKnowledgeBaseAsync(task.getTaskId(), knowledgeId);

        log.info("已创建知识库图谱构建任务: taskId={}, knowledgeId={}", task.getTaskId(), knowledgeId);
        return task;
    }

    @Override
    public GraphBuildTask rebuildFromKnowledgeBaseAsync(Long knowledgeId) {
        // 先删除旧数据
        deleteByKnowledgeBase(knowledgeId);
        // 创建异步构建任务
        return buildFromKnowledgeBaseAsync(knowledgeId);
    }

    @Override
    public GraphBuildTask getBuildTask(String taskId) {
        return taskManager.getTask(taskId).orElse(null);
    }

    // ==================== 同步构建方法（已弃用，保留兼容） ====================

    @Override
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public GraphBuildResponse buildFromKnowledgeBase(Long knowledgeId) {
        // 使用异步构建并等待完成（用于兼容旧接口）
        GraphBuildTask task = buildFromKnowledgeBaseAsync(knowledgeId);

        // 等待任务完成（最多等待5分钟）
        int maxWait = 300;
        int waited = 0;
        while (waited < maxWait) {
            GraphBuildTask currentTask = taskManager.getTask(task.getTaskId()).orElse(task);
            if (currentTask.getStatus() == GraphBuildTask.TaskStatus.COMPLETED) {
                return GraphBuildResponse.successForKnowledgeBase(knowledgeId,
                        currentTask.getEntityCount(),
                        currentTask.getRelationCount(),
                        currentTask.getConceptCount(),
                        currentTask.getKeywordCount(),
                        currentTask.getProcessedDocuments(),
                        currentTask.getTotalDocuments());
            } else if (currentTask.getStatus() == GraphBuildTask.TaskStatus.FAILED) {
                return GraphBuildResponse.failForKnowledgeBase(knowledgeId, currentTask.getErrorMessage());
            }

            try {
                Thread.sleep(1000);
                waited++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return GraphBuildResponse.failForKnowledgeBase(knowledgeId, "构建超时，请稍后查询任务状态");
    }

    @Override
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public GraphBuildResponse rebuildFromKnowledgeBase(Long knowledgeId) {
        // 先删除旧数据
        deleteByKnowledgeBase(knowledgeId);
        // 重新构建
        return buildFromKnowledgeBase(knowledgeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByKnowledgeBase(Long knowledgeId) {
        // 直接调用内部方法，从ES获取文档ID
        deleteByKnowledgeBaseInternal(knowledgeId);
    }

    @Override
    public GraphBuildResponse.KnowledgeGraphStatus getKnowledgeGraphStatus(Long knowledgeId) {
        // 从ES获取知识库的分块数量
        long chunkCount = vectorStoreService.countChunksByKnowledgeId(knowledgeId);

        // 获取所有文档ID
        Set<Long> documentIds = new HashSet<>();
        int pageSize = 100;
        int totalPages = (int) Math.ceil((double) chunkCount / pageSize);

        for (int page = 0; page < totalPages && page < 10; page++) { // 最多查询10页来获取文档ID
            List<org.springframework.ai.document.Document> chunks =
                    vectorStoreService.getChunksByKnowledgeId(knowledgeId, page, pageSize);
            if (chunks != null) {
                for (org.springframework.ai.document.Document chunk : chunks) {
                    Object docIdObj = chunk.getMetadata().get("document_id");
                    if (docIdObj != null) {
                        documentIds.add(((Number) docIdObj).longValue());
                    }
                }
            }
        }

        int totalDocumentCount = documentIds.size();
        int builtDocumentCount = 0;
        int nodeCount = 0;
        String lastBuildTime = null;

        for (Long documentId : documentIds) {
            Optional<DocumentNode> docNode = documentNodeRepository.findByDocumentId(documentId);
            if (docNode.isPresent()) {
                builtDocumentCount++;
                if (docNode.get().getCreateTime() != null) {
                    String buildTime = docNode.get().getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    if (lastBuildTime == null || buildTime.compareTo(lastBuildTime) > 0) {
                        lastBuildTime = buildTime;
                    }
                }
            }
        }

        // 统计节点数量 - 按知识库ID过滤
        try {
            nodeCount = (int) entityNodeRepository.findByKnowledgeBaseId(knowledgeId).size() +
                        (int) conceptNodeRepository.findTopByKnowledgeBaseId(knowledgeId, Integer.MAX_VALUE).size() +
                        (int) keywordNodeRepository.findTopByKnowledgeBaseId(knowledgeId, Integer.MAX_VALUE).size() +
                        builtDocumentCount;
        } catch (Exception e) {
            log.warn("统计节点数量失败", e);
        }

        return GraphBuildResponse.KnowledgeGraphStatus.builder()
                .knowledgeId(knowledgeId)
                .graphBuilt(builtDocumentCount > 0)
                .nodeCount(nodeCount)
                .relationshipCount(0)
                .lastBuildTime(lastBuildTime)
                .builtDocumentCount(builtDocumentCount)
                .totalDocumentCount(totalDocumentCount)
                .build();
    }

    // ==================== 私有方法 ====================

    private DocumentNode createDocumentNode(KnowledgeDocument document, Long knowledgeBaseId) {
        DocumentNode node = new DocumentNode();
        node.setDocumentId(document.getId());
        node.setName(document.getTitle());
        node.setTitle(document.getTitle());
        node.setFileType(document.getFileType());
        node.setUserId(document.getUserId());
        node.setCategoryId(document.getCategoryId());
        node.setKnowledgeBaseId(knowledgeBaseId);
        node.setDescription("文档: " + document.getTitle());
        node.setCreateTime(LocalDateTime.now());
        node.setUpdateTime(LocalDateTime.now());
        return documentNodeRepository.save(node);
    }

    private Map<String, EntityNode> createEntityNodes(List<EntityExtractionResult.EntityInfo> entities, Long documentId, Long knowledgeBaseId) {
        Map<String, EntityNode> entityMap = new HashMap<>();

        for (EntityExtractionResult.EntityInfo entityInfo : entities) {
            if (entityInfo.getName() == null || entityInfo.getName().isBlank()) {
                continue;
            }

            String key = entityInfo.getEntityType() + ":" + entityInfo.getName();

            // 检查是否已存在
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

            // 检查是否已存在同一知识库下的概念
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

            // 检查是否已存在同一知识库下的关键词
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

            // 建立关系
            if ("RELATED_TO".equals(relationInfo.getRelationType())) {
                sourceNode.getRelatedEntities().add(targetNode);
                entityNodeRepository.save(sourceNode);
            }
        }
    }

    /**
     * 内部删除方法，根据知识库ID直接删除Neo4j节点
     */
    private void deleteByKnowledgeBaseInternal(Long knowledgeId) {
        // 使用 CustomGraphRepository 直接按 knowledgeBaseId 删除
        try {
            customGraphRepository.deleteByKnowledgeBaseId(knowledgeId);
            log.info("删除知识库图谱数据: knowledgeId={}", knowledgeId);
        } catch (Exception e) {
            log.warn("删除知识库图谱数据失败: knowledgeId={}, error={}", knowledgeId, e.getMessage());
        }
    }
}
