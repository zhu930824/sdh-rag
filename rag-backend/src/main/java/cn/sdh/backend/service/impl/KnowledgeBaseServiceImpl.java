package cn.sdh.backend.service.impl;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.entity.KnowledgeDocumentRelation;
import cn.sdh.backend.mapper.KnowledgeBaseMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentRelationMapper;
import cn.sdh.backend.service.KnowledgeBaseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库服务实现
 */
@Slf4j
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private KnowledgeDocumentMapper documentMapper;

    @Autowired
    private KnowledgeDocumentRelationMapper relationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createKnowledgeBase(KnowledgeBase knowledgeBase) {
        knowledgeBase.setStatus(1);
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
    public boolean deleteKnowledgeBase(Long id, Long userId) {
        KnowledgeBase knowledgeBase = getKnowledgeBaseById(id);
        if (knowledgeBase == null) {
            throw new BusinessException("知识库不存在");
        }
        if (!knowledgeBase.getUserId().equals(userId)) {
            throw new BusinessException("无权删除该知识库");
        }
        // 删除关联关系
        relationMapper.deleteByKnowledgeId(id);
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
    public boolean uploadDocumentToKnowledgeBase(Long knowledgeId, KnowledgeDocument document) {
        // 保存文档
        document.setStatus(0);
        document.setCreateTime(LocalDateTime.now());
        document.setUpdateTime(LocalDateTime.now());
        documentMapper.insert(document);

        // 创建关联关系
        KnowledgeDocumentRelation relation = new KnowledgeDocumentRelation();
        relation.setKnowledgeId(knowledgeId);
        relation.setDocumentId(document.getId());
        relation.setCreateTime(LocalDateTime.now());

        return relationMapper.insert(relation) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean linkDocumentsToKnowledgeBase(Long knowledgeId, List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return true;
        }

        for (Long documentId : documentIds) {
            // 检查是否已关联
            LambdaQueryWrapper<KnowledgeDocumentRelation> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(KnowledgeDocumentRelation::getKnowledgeId, knowledgeId)
                   .eq(KnowledgeDocumentRelation::getDocumentId, documentId);

            if (relationMapper.selectCount(wrapper) > 0) {
                continue; // 已关联，跳过
            }

            // 创建关联
            KnowledgeDocumentRelation relation = new KnowledgeDocumentRelation();
            relation.setKnowledgeId(knowledgeId);
            relation.setDocumentId(documentId);
            relation.setCreateTime(LocalDateTime.now());
            relationMapper.insert(relation);
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unlinkDocumentFromKnowledgeBase(Long knowledgeId, Long documentId) {
        return relationMapper.deleteByKnowledgeIdAndDocumentId(knowledgeId, documentId) > 0;
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
}
