package cn.sdh.backend.service.impl;

import cn.sdh.backend.dto.EntityExtractionResult;
import cn.sdh.backend.dto.GraphBuildResponse;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.graph.extractor.EntityExtractor;
import cn.sdh.backend.graph.node.ConceptNode;
import cn.sdh.backend.graph.node.DocumentNode;
import cn.sdh.backend.graph.node.EntityNode;
import cn.sdh.backend.graph.node.KeywordNode;
import cn.sdh.backend.graph.repository.ConceptNodeRepository;
import cn.sdh.backend.graph.repository.DocumentNodeRepository;
import cn.sdh.backend.graph.repository.EntityNodeRepository;
import cn.sdh.backend.graph.repository.KeywordNodeRepository;
import cn.sdh.backend.service.GraphBuildService;
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
    private final KnowledgeService knowledgeService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final VectorStoreService vectorStoreService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GraphBuildResponse buildFromDocument(Long documentId) {
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
            DocumentNode documentNode = createDocumentNode(document);

            // 创建实体节点
            Map<String, EntityNode> entityMap = createEntityNodes(extractionResult.getEntities(), documentId);

            // 创建概念节点
            Map<String, ConceptNode> conceptMap = createConceptNodes(extractionResult.getConcepts());

            // 创建关键词节点
            Map<String, KeywordNode> keywordMap = createKeywordNodes(extractionResult.getKeywords(), documentId);

            // 建立文档与实体/概念/关键词的关系
            documentNode.setEntities(new HashSet<>(entityMap.values()));
            documentNode.setConcepts(new HashSet<>(conceptMap.values()));
            documentNode.setKeywords(new HashSet<>(keywordMap.values()));
            documentNodeRepository.save(documentNode);

            // 创建实体间关系
            createEntityRelations(extractionResult.getRelations(), entityMap);

            log.info("图谱构建完成: documentId={}, entities={}, relations={}, concepts={}, keywords={}",
                    documentId, entityMap.size(), extractionResult.getRelations().size(),
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

    private DocumentNode createDocumentNode(KnowledgeDocument document) {
        DocumentNode node = new DocumentNode();
        node.setDocumentId(document.getId());
        node.setName(document.getTitle());
        node.setTitle(document.getTitle());
        node.setFileType(document.getFileType());
        node.setUserId(document.getUserId());
        node.setCategoryId(document.getCategoryId());
        node.setDescription("文档: " + document.getTitle());
        node.setCreateTime(LocalDateTime.now());
        node.setUpdateTime(LocalDateTime.now());
        return documentNodeRepository.save(node);
    }

    private Map<String, EntityNode> createEntityNodes(List<EntityExtractionResult.EntityInfo> entities, Long documentId) {
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
            node.setFrequency(1);
            node.setCreateTime(LocalDateTime.now());
            node.setUpdateTime(LocalDateTime.now());

            entityMap.put(key, entityNodeRepository.save(node));
        }

        return entityMap;
    }

    private Map<String, ConceptNode> createConceptNodes(List<EntityExtractionResult.ConceptInfo> concepts) {
        Map<String, ConceptNode> conceptMap = new HashMap<>();

        for (EntityExtractionResult.ConceptInfo conceptInfo : concepts) {
            if (conceptInfo.getName() == null || conceptInfo.getName().isBlank()) {
                continue;
            }

            // 检查是否已存在
            ConceptNode existingNode = conceptNodeRepository.findByName(conceptInfo.getName()).orElse(null);
            if (existingNode != null) {
                existingNode.setWeight(existingNode.getWeight() + 1);
                conceptMap.put(conceptInfo.getName(), conceptNodeRepository.save(existingNode));
                continue;
            }

            ConceptNode node = new ConceptNode();
            node.setName(conceptInfo.getName());
            node.setDescription(conceptInfo.getDescription());
            node.setCategory(conceptInfo.getCategory());
            node.setWeight(1);
            node.setCreateTime(LocalDateTime.now());
            node.setUpdateTime(LocalDateTime.now());

            conceptMap.put(conceptInfo.getName(), conceptNodeRepository.save(node));
        }

        return conceptMap;
    }

    private Map<String, KeywordNode> createKeywordNodes(List<EntityExtractionResult.KeywordInfo> keywords, Long documentId) {
        Map<String, KeywordNode> keywordMap = new HashMap<>();

        for (EntityExtractionResult.KeywordInfo keywordInfo : keywords) {
            if (keywordInfo.getKeyword() == null || keywordInfo.getKeyword().isBlank()) {
                continue;
            }

            // 检查是否已存在
            KeywordNode existingNode = keywordNodeRepository.findByName(keywordInfo.getKeyword()).orElse(null);
            if (existingNode != null) {
                existingNode.setFrequency(existingNode.getFrequency() + 1);
                keywordMap.put(keywordInfo.getKeyword(), keywordNodeRepository.save(existingNode));
                continue;
            }

            KeywordNode node = new KeywordNode();
            node.setName(keywordInfo.getKeyword());
            node.setSourceDocumentId(documentId);
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

    // ==================== 知识库图谱构建 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GraphBuildResponse buildFromKnowledgeBase(Long knowledgeId) {
        try {
            // 从ES获取知识库的分块数量
            long chunkCount = vectorStoreService.countChunksByKnowledgeId(knowledgeId);
            if (chunkCount == 0) {
                return GraphBuildResponse.failForKnowledgeBase(knowledgeId, "知识库下没有分块数据，请先处理文档");
            }

            log.info("开始构建知识库图谱: knowledgeId={}, chunkCount={}", knowledgeId, chunkCount);

            // 删除知识库旧的图谱数据
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

            // 为每个文档构建图谱
            int totalDocuments = documentTextMap.size();
            int processedDocuments = 0;

            for (Map.Entry<Long, StringBuilder> entry : documentTextMap.entrySet()) {
                Long documentId = entry.getKey();
                String fullText = entry.getValue().toString();

                // 截取文本，避免过长（LLM有token限制）
                String truncatedText = fullText.length() > 8000 ? fullText.substring(0, 8000) : fullText;

                try {
                    // 提取实体和关系
                    EntityExtractionResult extractionResult = entityExtractor.extract(truncatedText, documentId);

                    // 创建实体节点
                    Map<String, EntityNode> entityMap = createEntityNodes(extractionResult.getEntities(), documentId);
                    totalEntities += entityMap.size();

                    // 创建概念节点
                    Map<String, ConceptNode> conceptMap = createConceptNodes(extractionResult.getConcepts());
                    totalConcepts += conceptMap.size();

                    // 创建关键词节点
                    Map<String, KeywordNode> keywordMap = createKeywordNodes(extractionResult.getKeywords(), documentId);
                    totalKeywords += keywordMap.size();

                    // 创建实体间关系
                    createEntityRelations(extractionResult.getRelations(), entityMap);
                    totalRelations += extractionResult.getRelations().size();

                    // 创建文档节点并关联
                    createDocumentNodeWithRelations(documentId, entityMap, conceptMap, keywordMap);

                    processedDocuments++;
                    log.info("文档图谱构建完成: documentId={}, entities={}, concepts={}, keywords={}",
                            documentId, entityMap.size(), conceptMap.size(), keywordMap.size());

                } catch (Exception e) {
                    log.warn("构建文档图谱失败: documentId={}, error={}", documentId, e.getMessage());
                }
            }

            log.info("知识库图谱构建完成: knowledgeId={}, processedDocuments={}, totalEntities={}, totalRelations={}",
                    knowledgeId, processedDocuments, totalEntities, totalRelations);

            return GraphBuildResponse.successForKnowledgeBase(knowledgeId,
                    totalEntities, totalRelations, totalConcepts, totalKeywords,
                    processedDocuments, totalDocuments);

        } catch (Exception e) {
            log.error("知识库图谱构建失败: knowledgeId={}", knowledgeId, e);
            return GraphBuildResponse.failForKnowledgeBase(knowledgeId, "知识库图谱构建失败: " + e.getMessage());
        }
    }

    /**
     * 创建文档节点并关联实体、概念、关键词
     */
    private DocumentNode createDocumentNodeWithRelations(Long documentId,
                                                          Map<String, EntityNode> entityMap,
                                                          Map<String, ConceptNode> conceptMap,
                                                          Map<String, KeywordNode> keywordMap) {
        DocumentNode node = new DocumentNode();
        node.setDocumentId(documentId);
        node.setName("文档-" + documentId);
        node.setTitle("文档-" + documentId);
        node.setDescription("知识库文档");
        node.setCreateTime(LocalDateTime.now());
        node.setUpdateTime(LocalDateTime.now());

        // 关联实体、概念、关键词
        node.setEntities(new HashSet<>(entityMap.values()));
        node.setConcepts(new HashSet<>(conceptMap.values()));
        node.setKeywords(new HashSet<>(keywordMap.values()));

        return documentNodeRepository.save(node);
    }

    /**
     * 内部删除方法，根据ES中的文档ID删除Neo4j节点
     */
    private void deleteByKnowledgeBaseInternal(Long knowledgeId) {
        // 从ES获取知识库的所有分块
        long chunkCount = vectorStoreService.countChunksByKnowledgeId(knowledgeId);
        if (chunkCount == 0) {
            return;
        }

        Set<Long> documentIds = new HashSet<>();
        int pageSize = 100;
        int totalPages = (int) Math.ceil((double) chunkCount / pageSize);

        for (int page = 0; page < totalPages; page++) {
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

        // 删除这些文档的图谱节点
        for (Long documentId : documentIds) {
            try {
                documentNodeRepository.deleteByDocumentId(documentId);
            } catch (Exception e) {
                log.warn("删除文档图谱节点失败: documentId={}", documentId);
            }
        }
    }

    @Override
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

        // 统计节点数量
        try {
            nodeCount = (int) entityNodeRepository.count() +
                        (int) conceptNodeRepository.count() +
                        (int) keywordNodeRepository.count() +
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
}
