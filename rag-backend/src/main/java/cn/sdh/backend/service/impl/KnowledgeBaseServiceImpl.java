package cn.sdh.backend.service.impl;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.dto.DocumentLinkConfig;
import cn.sdh.backend.dto.KnowledgeBaseListVO;
import cn.sdh.backend.dto.KnowledgeChunkVO;
import cn.sdh.backend.dto.KnowledgeDocumentVO;
import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.entity.KnowledgeChunk;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.entity.KnowledgeDocumentRelation;
import cn.sdh.backend.entity.Tag;
import cn.sdh.backend.mapper.KnowledgeBaseMapper;
import cn.sdh.backend.mapper.KnowledgeChunkMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentRelationMapper;
import cn.sdh.backend.service.DocumentProcessService;
import cn.sdh.backend.service.KnowledgeBaseService;
import cn.sdh.backend.service.KnowledgeBaseTagService;
import cn.sdh.backend.service.TagService;
import cn.sdh.backend.service.VectorStoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    @Value(value = "${knowledge.default.chunk-size}")
    private Integer DEFAULT_CHUNK_SIZE;

    @Value(value = "${knowledge.default.chunk-overlap}")
    private Integer DEFAULT_CHUNK_OVERLAP;

    @Value(value = "${knowledge.default.embeddingModel}")
    private String DEFAULT_EMBEDDING_MODEL;

    @Value(value = "${knowledge.default.rankModel}")
    private String DEFAULT_RANK_MODEL;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeDocumentRelationMapper relationMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final DocumentProcessService documentProcessService;
    private final VectorStoreService vectorStoreService;
    private final KnowledgeBaseTagService knowledgeBaseTagService;
    private final TagService tagService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createKnowledgeBase(KnowledgeBase knowledgeBase) {
        defaultKnowledgeConfig(knowledgeBase);
        return knowledgeBaseMapper.insert(knowledgeBase) > 0;
    }

    private void defaultKnowledgeConfig(KnowledgeBase knowledgeBase) {
        // 设置默认值
        if (knowledgeBase.getChunkSize() == null) {
            knowledgeBase.setChunkSize(DEFAULT_CHUNK_SIZE);
        }
        if (knowledgeBase.getChunkOverlap() == null) {
            knowledgeBase.setChunkOverlap(DEFAULT_CHUNK_OVERLAP);
        }
        if (knowledgeBase.getEmbeddingModel() == null) {
            knowledgeBase.setEmbeddingModel(DEFAULT_EMBEDDING_MODEL);
        }
        if (knowledgeBase.getStatus() == null) {
            knowledgeBase.setStatus(1);
        }
        if (knowledgeBase.getIsPublic() == null) {
            knowledgeBase.setIsPublic(false);
        }
        // 设置检索配置默认值
        if (knowledgeBase.getRankModel() == null){
            knowledgeBase.setRankModel(DEFAULT_RANK_MODEL);
        }

        if (knowledgeBase.getSimilarityThreshold() == null) {
            knowledgeBase.setSimilarityThreshold(0.7);
        }
        if (knowledgeBase.getKeywordTopK() == null) {
            knowledgeBase.setKeywordTopK(10);
        }
        if (knowledgeBase.getVectorTopK() == null) {
            knowledgeBase.setVectorTopK(10);
        }
        if (knowledgeBase.getKeywordWeight() == null) {
            knowledgeBase.setKeywordWeight(0.3);
        }
        if (knowledgeBase.getVectorWeight() == null) {
            knowledgeBase.setVectorWeight(0.7);
        }
        if (knowledgeBase.getEnableRewrite() == null) {
            knowledgeBase.setEnableRewrite(false);
        }

        knowledgeBase.setCreateTime(LocalDateTime.now());
        knowledgeBase.setUpdateTime(LocalDateTime.now());
    }

    @Override
    public List<KnowledgeBase> getKnowledgeBaseList(Long userId) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getUserId, userId)
                .eq(KnowledgeBase::getStatus, 1)
                .orderByDesc(KnowledgeBase::getCreateTime);
        return knowledgeBaseMapper.selectList(wrapper);
    }

    @Override
    public Page<KnowledgeBase> getKnowledgeBasePage(Long userId, int page, int size, String keyword, Integer status) {
        Page<KnowledgeBase> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(KnowledgeBase::getUserId, userId);

        if (StringUtils.hasText(keyword)) {
            wrapper.like(KnowledgeBase::getName, keyword);
        }
        if (status != null) {
            wrapper.eq(KnowledgeBase::getStatus, status);
        }

        wrapper.orderByDesc(KnowledgeBase::getCreateTime);

        return knowledgeBaseMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public Page<KnowledgeBaseListVO> getKnowledgeBasePageWithStats(Long userId, int page, int size, String keyword, Integer status) {
        // 先获取知识库分页
        Page<KnowledgeBase> kbPage = getKnowledgeBasePage(userId, page, size, keyword, status);

        // 转换为 VO
        Page<KnowledgeBaseListVO> voPage = new Page<>(page, size);
        voPage.setTotal(kbPage.getTotal());
        voPage.setPages(kbPage.getPages());

        if (kbPage.getRecords().isEmpty()) {
            voPage.setRecords(List.of());
            return voPage;
        }

        // 批量查询文档数和分块数
        List<KnowledgeBaseListVO> voList = kbPage.getRecords().stream()
                .map(kb -> {
                    KnowledgeBaseListVO vo = KnowledgeBaseListVO.fromEntity(kb);

                    // 获取关联的文档ID列表
                    List<Long> docIds = relationMapper.selectDocumentIdsByKnowledgeId(kb.getId());
                    vo.setDocumentCount(docIds.size());

                    // 统计分块数
                    int totalChunks = 0;
                    for (Long docId : docIds) {
                        KnowledgeDocumentRelation relation = relationMapper.selectByKnowledgeIdAndDocumentId(kb.getId(), docId);
                        if (relation != null && relation.getChunkCount() != null) {
                            totalChunks += relation.getChunkCount();
                        }
                    }
                    vo.setChunkCount(totalChunks);

                    return vo;
                })
                .toList();

        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public KnowledgeBase getKnowledgeBaseById(Long id) {
        return knowledgeBaseMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateKnowledgeBase(KnowledgeBase knowledgeBase) {
        knowledgeBase.setUpdateTime(LocalDateTime.now());
        return knowledgeBaseMapper.updateById(knowledgeBase) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateKnowledgeBaseConfig(Long id, Integer chunkSize, Integer chunkOverlap, String embeddingModel) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            return false;
        }

        if (chunkSize != null) {
            kb.setChunkSize(chunkSize);
        }
        if (chunkOverlap != null) {
            kb.setChunkOverlap(chunkOverlap);
        }
        if (embeddingModel != null) {
            kb.setEmbeddingModel(embeddingModel);
        }
        kb.setUpdateTime(LocalDateTime.now());

        return knowledgeBaseMapper.updateById(kb) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateKnowledgeBaseFullConfig(Long id, KnowledgeBase config) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            return false;
        }

        // 更新切分配置
        if (config.getChunkSize() != null) {
            kb.setChunkSize(config.getChunkSize());
        }
        if (config.getChunkOverlap() != null) {
            kb.setChunkOverlap(config.getChunkOverlap());
        }
        if (config.getEmbeddingModel() != null) {
            kb.setEmbeddingModel(config.getEmbeddingModel());
        }

        // 更新检索配置
        if (config.getRankModel() != null) {
            kb.setRankModel(config.getRankModel());
        }
        if (config.getEnableRewrite() != null) {
            kb.setEnableRewrite(config.getEnableRewrite());
        }
        if (config.getSimilarityThreshold() != null) {
            kb.setSimilarityThreshold(config.getSimilarityThreshold());
        }
        if (config.getKeywordTopK() != null) {
            kb.setKeywordTopK(config.getKeywordTopK());
        }
        if (config.getVectorTopK() != null) {
            kb.setVectorTopK(config.getVectorTopK());
        }
        if (config.getKeywordWeight() != null) {
            kb.setKeywordWeight(config.getKeywordWeight());
        }
        if (config.getVectorWeight() != null) {
            kb.setVectorWeight(config.getVectorWeight());
        }

        kb.setUpdateTime(LocalDateTime.now());
        return knowledgeBaseMapper.updateById(kb) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteKnowledgeBase(Long id, Long userId) {
        KnowledgeBase knowledgeBase = getKnowledgeBaseById(id);
        if (knowledgeBase == null) {
            throw new BusinessException("知识库不存在");
        }
        if (!knowledgeBase.getUserId().equals(userId)) {
            throw new BusinessException("无权删除该知识库");
        }

        // 获取所有关联的文档
        List<Long> documentIds = relationMapper.selectDocumentIdsByKnowledgeId(id);

        // 取消每个文档的关联（清理向量）
        for (Long documentId : documentIds) {
            documentProcessService.unlinkDocumentFromKnowledge(documentId, id);
        }

        // 删除知识库
        return knowledgeBaseMapper.deleteById(id) > 0;
    }

    @Override
    public Page<KnowledgeDocument> getDocumentsByKnowledgeId(Long knowledgeId, int page, int size) {
        // 获取关联的文档ID列表
        List<Long> documentIds = relationMapper.selectDocumentIdsByKnowledgeId(knowledgeId);

        Page<KnowledgeDocument> pageParam = new Page<>(page, size);
        if (documentIds == null || documentIds.isEmpty()) {
            return pageParam;
        }

        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(KnowledgeDocument::getId, documentIds)
                .orderByDesc(KnowledgeDocument::getCreateTime);

        return documentMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public Page<KnowledgeDocumentVO> getDocumentDetailsByKnowledgeId(Long knowledgeId, int page, int size) {
        // 获取关联记录列表
        LambdaQueryWrapper<KnowledgeDocumentRelation> relationWrapper = new LambdaQueryWrapper<>();
        relationWrapper.eq(KnowledgeDocumentRelation::getKnowledgeId, knowledgeId)
                .orderByDesc(KnowledgeDocumentRelation::getCreateTime);

        Page<KnowledgeDocumentRelation> relationPage = new Page<>(page, size);
        relationMapper.selectPage(relationPage, relationWrapper);

        // 转换为 VO
        Page<KnowledgeDocumentVO> voPage = new Page<>(page, size);
        voPage.setTotal(relationPage.getTotal());
        voPage.setPages(relationPage.getPages());

        if (relationPage.getRecords().isEmpty()) {
            voPage.setRecords(List.of());
            return voPage;
        }

        // 批量获取文档信息
        List<Long> documentIds = relationPage.getRecords().stream()
                .map(KnowledgeDocumentRelation::getDocumentId)
                .distinct()
                .toList();

        Map<Long, KnowledgeDocument> documentMap = new HashMap<>();
        if (!documentIds.isEmpty()) {
            List<KnowledgeDocument> documents = documentMapper.selectBatchIds(documentIds);
            for (KnowledgeDocument doc : documents) {
                documentMap.put(doc.getId(), doc);
            }
        }

        // 转换记录
        List<KnowledgeDocumentVO> voList = relationPage.getRecords().stream()
                .map(relation -> {
                    KnowledgeDocument doc = documentMap.get(relation.getDocumentId());
                    return KnowledgeDocumentVO.fromEntity(doc, relation);
                })
                .toList();

        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean linkDocumentsToKnowledgeBase(Long knowledgeId, List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return true;
        }

        KnowledgeBase knowledgeBase = getKnowledgeBaseById(knowledgeId);
        if (knowledgeBase == null) {
            throw new BusinessException("知识库不存在");
        }

        for (Long documentId : documentIds) {
            // 检查是否已关联
            KnowledgeDocumentRelation existing = relationMapper.selectByKnowledgeIdAndDocumentId(knowledgeId, documentId);
            if (existing != null) {
                continue; // 已关联，跳过
            }

            // 创建关联记录
            KnowledgeDocumentRelation relation = new KnowledgeDocumentRelation();
            relation.setKnowledgeId(knowledgeId);
            relation.setDocumentId(documentId);
            relation.setProcessStatus(0); // 待处理
            relation.setChunkCount(0);
            relation.setChunkSize(knowledgeBase.getChunkSize());
            relation.setChunkOverlap(knowledgeBase.getChunkOverlap());
            relation.setEmbeddingModel(knowledgeBase.getEmbeddingModel());
            relation.setCreateTime(LocalDateTime.now());
            relationMapper.insert(relation);

            // 异步处理文档
            documentProcessService.processDocumentForKnowledge(
                    documentId,
                    knowledgeId,
                    knowledgeBase.getChunkSize(),
                    knowledgeBase.getChunkOverlap()
            );
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean linkDocumentsWithConfig(Long knowledgeId, List<DocumentLinkConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return true;
        }

        KnowledgeBase knowledgeBase = getKnowledgeBaseById(knowledgeId);
        if (knowledgeBase == null) {
            throw new BusinessException("知识库不存在");
        }

        for (DocumentLinkConfig config : configs) {
            Long documentId = config.getDocumentId();
            if (documentId == null) {
                continue;
            }

            // 设置默认切分模式
            if (config.getChunkMode() == null) {
                config.setChunkMode("default");
            }

            // 检查是否已关联
            KnowledgeDocumentRelation existing = relationMapper.selectByKnowledgeIdAndDocumentId(knowledgeId, documentId);
            if (existing != null) {
                // 已关联，更新配置
                existing.setChunkMode(config.getChunkMode());

                // 根据切分模式设置参数
                if ("custom".equals(config.getChunkMode())) {
                    // 设置切分方式
                    existing.setSplitType(config.getSplitType() != null ? config.getSplitType() : "length");

                    // 根据切分方式设置参数
                    if ("length".equals(config.getSplitType()) || config.getSplitType() == null) {
                        existing.setChunkSize(config.getChunkSize() != null ? config.getChunkSize() : knowledgeBase.getChunkSize());
                        existing.setChunkOverlap(config.getChunkOverlap() != null ? config.getChunkOverlap() : knowledgeBase.getChunkOverlap());
                    } else if ("page".equals(config.getSplitType())) {
                        existing.setPagesPerChunk(config.getPagesPerChunk());
                    } else if ("heading".equals(config.getSplitType())) {
                        existing.setHeadingLevels(cn.hutool.json.JSONUtil.toJsonStr(config.getHeadingLevels()));
                    } else if ("regex".equals(config.getSplitType())) {
                        existing.setRegexPattern(config.getRegexPattern());
                    }
                } else if ("default".equals(config.getChunkMode())) {
                    existing.setChunkSize(knowledgeBase.getChunkSize());
                    existing.setChunkOverlap(knowledgeBase.getChunkOverlap());
                }
                // smart 模式不需要设置 chunkSize 和 chunkOverlap

                if (config.getEmbeddingModel() != null) {
                    existing.setEmbeddingModel(config.getEmbeddingModel());
                } else {
                    existing.setEmbeddingModel(knowledgeBase.getEmbeddingModel());
                }

                // 保存智能配置
                if (config.getSmartConfig() != null) {
                    existing.setSmartConfig(cn.hutool.json.JSONUtil.toJsonStr(config.getSmartConfig()));
                }

                relationMapper.updateById(existing);

                // 异步重新处理文档
                documentProcessService.processDocumentForKnowledge(documentId, knowledgeId, config);
                continue;
            }

            // 创建关联记录
            KnowledgeDocumentRelation relation = new KnowledgeDocumentRelation();
            relation.setKnowledgeId(knowledgeId);
            relation.setDocumentId(documentId);
            relation.setProcessStatus(0); // 待处理
            relation.setChunkCount(0);
            relation.setChunkMode(config.getChunkMode());

            // 根据切分模式设置参数
            if ("custom".equals(config.getChunkMode())) {
                // 设置切分方式
                relation.setSplitType(config.getSplitType() != null ? config.getSplitType() : "length");

                // 根据切分方式设置参数
                if ("length".equals(config.getSplitType()) || config.getSplitType() == null) {
                    relation.setChunkSize(config.getChunkSize() != null ? config.getChunkSize() : knowledgeBase.getChunkSize());
                    relation.setChunkOverlap(config.getChunkOverlap() != null ? config.getChunkOverlap() : knowledgeBase.getChunkOverlap());
                } else if ("page".equals(config.getSplitType())) {
                    relation.setPagesPerChunk(config.getPagesPerChunk());
                } else if ("heading".equals(config.getSplitType())) {
                    relation.setHeadingLevels(cn.hutool.json.JSONUtil.toJsonStr(config.getHeadingLevels()));
                } else if ("regex".equals(config.getSplitType())) {
                    relation.setRegexPattern(config.getRegexPattern());
                }
            } else if ("default".equals(config.getChunkMode())) {
                relation.setChunkSize(knowledgeBase.getChunkSize());
                relation.setChunkOverlap(knowledgeBase.getChunkOverlap());
            }
            // smart 模式在处理时根据 smartConfig 动态调整

            relation.setEmbeddingModel(config.getEmbeddingModel() != null ? config.getEmbeddingModel() : knowledgeBase.getEmbeddingModel());
            relation.setCreateTime(LocalDateTime.now());

            // 保存智能配置
            if (config.getSmartConfig() != null) {
                relation.setSmartConfig(cn.hutool.json.JSONUtil.toJsonStr(config.getSmartConfig()));
            }

            relationMapper.insert(relation);

            // 异步处理文档
            documentProcessService.processDocumentForKnowledge(documentId, knowledgeId, config);
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateDocumentLinkConfig(Long knowledgeId, Long documentId, DocumentLinkConfig config) {
        KnowledgeDocumentRelation relation = relationMapper.selectByKnowledgeIdAndDocumentId(knowledgeId, documentId);
        if (relation == null) {
            throw new BusinessException("文档未关联到该知识库");
        }

        KnowledgeBase knowledgeBase = getKnowledgeBaseById(knowledgeId);

        // 更新切分模式
        if (config.getChunkMode() != null) {
            relation.setChunkMode(config.getChunkMode());

            // 根据切分模式设置参数
            if ("custom".equals(config.getChunkMode())) {
                // 更新切分方式
                if (config.getSplitType() != null) {
                    relation.setSplitType(config.getSplitType());
                }

                // 根据切分方式更新参数
                if ("length".equals(config.getSplitType()) || config.getSplitType() == null) {
                    if (config.getChunkSize() != null) {
                        relation.setChunkSize(config.getChunkSize());
                    }
                    if (config.getChunkOverlap() != null) {
                        relation.setChunkOverlap(config.getChunkOverlap());
                    }
                } else if ("page".equals(config.getSplitType())) {
                    if (config.getPagesPerChunk() != null) {
                        relation.setPagesPerChunk(config.getPagesPerChunk());
                    }
                } else if ("heading".equals(config.getSplitType())) {
                    if (config.getHeadingLevels() != null) {
                        relation.setHeadingLevels(cn.hutool.json.JSONUtil.toJsonStr(config.getHeadingLevels()));
                    }
                } else if ("regex".equals(config.getSplitType())) {
                    if (config.getRegexPattern() != null) {
                        relation.setRegexPattern(config.getRegexPattern());
                    }
                }
            } else if ("default".equals(config.getChunkMode())) {
                if (knowledgeBase != null) {
                    relation.setChunkSize(knowledgeBase.getChunkSize());
                    relation.setChunkOverlap(knowledgeBase.getChunkOverlap());
                }
            }
            // smart 模式不需要设置 chunkSize 和 chunkOverlap
        }

        if (config.getEmbeddingModel() != null) {
            relation.setEmbeddingModel(config.getEmbeddingModel());
        } else if (knowledgeBase != null) {
            relation.setEmbeddingModel(knowledgeBase.getEmbeddingModel());
        }

        return relationMapper.updateById(relation) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unlinkDocumentFromKnowledgeBase(Long knowledgeId, Long documentId) {
        // 取消文档关联（清理向量）
        documentProcessService.unlinkDocumentFromKnowledge(documentId, knowledgeId);
        return true;
    }

    @Override
    public void reprocessDocument(Long knowledgeId, Long documentId) {
        documentProcessService.reprocessDocument(documentId, knowledgeId);
    }

    @Override
    public Page<KnowledgeDocument> getAllDocumentsForLinking(Long userId, int page, int size, Long excludeKnowledgeId, String keyword) {
        Page<KnowledgeDocument> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(KnowledgeDocument::getUserId, userId);

        // 如果指定了排除的知识库，则排除已关联到该知识库的文档
        if (excludeKnowledgeId != null) {
            List<Long> linkedDocumentIds = relationMapper.selectDocumentIdsByKnowledgeId(excludeKnowledgeId);
            if (linkedDocumentIds != null && !linkedDocumentIds.isEmpty()) {
                wrapper.notIn(KnowledgeDocument::getId, linkedDocumentIds);
            }
        }

        // 关键词搜索
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(KnowledgeDocument::getTitle, keyword);
        }

        wrapper.orderByDesc(KnowledgeDocument::getCreateTime);

        return documentMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public KnowledgeDocumentRelation getDocumentProcessStatus(Long knowledgeId, Long documentId) {
        return relationMapper.selectByKnowledgeIdAndDocumentId(knowledgeId, documentId);
    }

    @Override
    public KnowledgeBaseStats getKnowledgeBaseStats(Long userId) {
        KnowledgeBaseStats stats = new KnowledgeBaseStats();

        // 获取用户的所有知识库
        LambdaQueryWrapper<KnowledgeBase> kbWrapper = new LambdaQueryWrapper<>();
        kbWrapper.eq(KnowledgeBase::getUserId, userId);
        List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.selectList(kbWrapper);

        stats.totalBases = knowledgeBases.size();
        stats.activeBases = knowledgeBases.stream()
                .filter(kb -> kb.getStatus() != null && kb.getStatus() == 1)
                .count();

        // 获取关联的文档总数
        long totalDocs = 0;
        long totalChunks = 0;

        for (KnowledgeBase kb : knowledgeBases) {
            List<Long> docIds = relationMapper.selectDocumentIdsByKnowledgeId(kb.getId());
            totalDocs += docIds.size();

            // 统计分块数
            for (Long docId : docIds) {
                KnowledgeDocumentRelation relation = relationMapper.selectByKnowledgeIdAndDocumentId(kb.getId(), docId);
                if (relation != null && relation.getChunkCount() != null) {
                    totalChunks += relation.getChunkCount();
                }
            }
        }

        stats.totalDocuments = totalDocs;
        stats.totalChunks = totalChunks;

        return stats;
    }

    @Override
    public Page<KnowledgeChunkVO> getChunksByKnowledgeId(Long knowledgeId, int page, int size) {
        // 从 ES 查询分块数据
        List<org.springframework.ai.document.Document> esDocs = vectorStoreService.getChunksByKnowledgeId(knowledgeId, page - 1, size);
        long total = vectorStoreService.countChunksByKnowledgeId(knowledgeId);

        // 转换为 VO
        Page<KnowledgeChunkVO> voPage = new Page<>(page, size);
        voPage.setTotal(total);
        voPage.setPages((total + size - 1) / size);

        if (esDocs.isEmpty()) {
            voPage.setRecords(List.of());
            return voPage;
        }

        // 批量获取文档信息
        List<Long> documentIds = esDocs.stream()
                .map(doc -> {
                    Object docId = doc.getMetadata().get("document_id");
                    if (docId instanceof Number) {
                        return ((Number) docId).longValue();
                    }
                    return null;
                })
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, KnowledgeDocument> documentMap = new HashMap<>();
        if (!documentIds.isEmpty()) {
            List<KnowledgeDocument> documents = documentMapper.selectBatchIds(documentIds);
            for (KnowledgeDocument doc : documents) {
                documentMap.put(doc.getId(), doc);
            }
        }

        // 转换记录
        List<KnowledgeChunkVO> voList = new java.util.ArrayList<>();
        for (org.springframework.ai.document.Document esDoc : esDocs) {
            KnowledgeChunkVO vo = new KnowledgeChunkVO();

            // 从 ES metadata 获取信息
            Object chunkId = esDoc.getMetadata().get("chunk_id");
            Object docId = esDoc.getMetadata().get("document_id");
            Object vectorId = esDoc.getMetadata().get("id");
            Object createTime = esDoc.getMetadata().get("create_time");
            Object chunkIndex = esDoc.getMetadata().get("chunk_index");

            // 设置 ID（使用 vectorId 或生成临时 ID）
            if (chunkId instanceof Number) {
                vo.setId(((Number) chunkId).longValue());
            }

            if (docId instanceof Number) {
                vo.setDocumentId(((Number) docId).longValue());
                KnowledgeDocument doc = documentMap.get(vo.getDocumentId());
                vo.setDocumentTitle(doc != null ? doc.getTitle() : "未知文档");
            }

            vo.setKnowledgeId(knowledgeId);
            // 优先使用 ES 中存储的 chunk_index，否则使用 chunk_id 作为排序依据
            if (chunkIndex instanceof Number) {
                vo.setChunkIndex(((Number) chunkIndex).intValue());
            } else if (chunkId instanceof Number) {
                // 旧数据没有 chunk_index，用 chunk_id 作为序号参考
                vo.setChunkIndex(((Number) chunkId).intValue());
            }
            vo.setContent(esDoc.getText());
            vo.setChunkSize(esDoc.getText() != null ? esDoc.getText().length() : 0);
            vo.setVectorId(vectorId != null ? vectorId.toString() : null);
            vo.setCreateTime(createTime != null ? createTime.toString() : null);

            voList.add(vo);
        }

        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addTagToKnowledgeBase(Long knowledgeId, Long tagId) {
        return knowledgeBaseTagService.addTagToKnowledgeBase(knowledgeId, tagId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeTagFromKnowledgeBase(Long knowledgeId, Long tagId) {
        return knowledgeBaseTagService.removeTagFromKnowledgeBase(knowledgeId, tagId);
    }

    @Override
    public List<Tag> getTagsByKnowledgeId(Long knowledgeId) {
        List<Long> tagIds = knowledgeBaseTagService.getTagIdsByKnowledgeBaseId(knowledgeId);
        if (tagIds.isEmpty()) {
            return List.of();
        }
        return tagService.listByIds(tagIds);
    }
}
