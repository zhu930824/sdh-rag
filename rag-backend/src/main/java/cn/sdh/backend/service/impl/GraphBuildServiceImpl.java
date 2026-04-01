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
import cn.sdh.backend.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
}
