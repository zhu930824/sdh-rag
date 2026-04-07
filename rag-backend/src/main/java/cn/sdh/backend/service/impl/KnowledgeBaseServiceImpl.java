package cn.sdh.backend.service.impl;

import cn.sdh.backend.common.exception.BusinessException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

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
        // 设置默认值
        if (knowledgeBase.getChunkSize() == null) {
            knowledgeBase.setChunkSize(500);
        }
        if (knowledgeBase.getChunkOverlap() == null) {
            knowledgeBase.setChunkOverlap(50);
        }
        if (knowledgeBase.getEmbeddingModel() == null) {
            knowledgeBase.setEmbeddingModel("text-embedding-ada-002");
        }
        if (knowledgeBase.getStatus() == null) {
            knowledgeBase.setStatus(1);
        }
        if (knowledgeBase.getIsPublic() == null) {
            knowledgeBase.setIsPublic(false);
        }

        knowledgeBase.setCreateTime(LocalDateTime.now());
        knowledgeBase.setUpdateTime(LocalDateTime.now());

        return knowledgeBaseMapper.insert(knowledgeBase) > 0;
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
    public boolean unlinkDocumentFromKnowledgeBase(Long knowledgeId, Long documentId) {
        // 取消文档关联（清理向量）
        documentProcessService.unlinkDocumentFromKnowledge(documentId, knowledgeId);
        return true;
    }

    @Override
    public Page<KnowledgeDocument> getAllDocumentsForLinking(Long userId, int page, int size, Long excludeKnowledgeId) {
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
    public Page<KnowledgeChunk> getChunksByKnowledgeId(Long knowledgeId, int page, int size) {
        Page<KnowledgeChunk> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<KnowledgeChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeChunk::getKnowledgeId, knowledgeId)
                .orderByAsc(KnowledgeChunk::getDocumentId)
                .orderByAsc(KnowledgeChunk::getChunkIndex);

        return chunkMapper.selectPage(pageParam, wrapper);
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
