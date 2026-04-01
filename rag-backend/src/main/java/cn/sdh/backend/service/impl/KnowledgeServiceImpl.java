package cn.sdh.backend.service.impl;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.DocumentCategory;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.mapper.DocumentCategoryMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentMapper;
import cn.sdh.backend.service.KnowledgeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
public class KnowledgeServiceImpl implements KnowledgeService {

    @Autowired
    private KnowledgeDocumentMapper documentMapper;

    @Autowired
    private DocumentCategoryMapper categoryMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean uploadDocument(KnowledgeDocument document) {
        document.setStatus(0); // 处理中
        document.setCreateTime(LocalDateTime.now());
        document.setUpdateTime(LocalDateTime.now());
        return documentMapper.insert(document) > 0;
    }

    @Override
    public Page<KnowledgeDocument> getDocumentList(int page, int size, Long categoryId, Long userId) {
        Page<KnowledgeDocument> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        
        wrapper.eq(KnowledgeDocument::getUserId, userId);
        
        if (categoryId != null) {
            wrapper.eq(KnowledgeDocument::getCategoryId, categoryId);
        }
        
        wrapper.orderByDesc(KnowledgeDocument::getCreateTime);
        
        return documentMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public Page<KnowledgeDocument> searchDocuments(String keyword, int page, int size, Long userId) {
        Page<KnowledgeDocument> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        
        wrapper.eq(KnowledgeDocument::getUserId, userId);
        
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(KnowledgeDocument::getTitle, keyword)
                    .or()
                    .like(KnowledgeDocument::getContent, keyword));
        }
        
        wrapper.orderByDesc(KnowledgeDocument::getCreateTime);
        
        return documentMapper.selectPage(pageParam, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDocument(Long id, Long userId) {
        // 验证文档所有权
        KnowledgeDocument document = getDocumentById(id);
        if (document == null) {
            throw new BusinessException("文档不存在");
        }
        
        if (!document.getUserId().equals(userId)) {
            throw new BusinessException("无权删除该文档");
        }
        
        return documentMapper.deleteById(id) > 0;
    }

    @Override
    public KnowledgeDocument getDocumentById(Long id) {
        return documentMapper.selectById(id);
    }

    @Override
    public List<DocumentCategory> getAllCategories() {
        LambdaQueryWrapper<DocumentCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(DocumentCategory::getSort);
        return categoryMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createCategory(DocumentCategory category) {
        category.setCreateTime(LocalDateTime.now());
        return categoryMapper.insert(category) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateDocumentStatus(Long id, Integer status) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(id);
        document.setStatus(status);
        document.setUpdateTime(LocalDateTime.now());
        return documentMapper.updateById(document) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuildAllIndexes() {
        log.info("开始重建所有索引");
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDocument::getStatus, 1);
        List<KnowledgeDocument> documents = documentMapper.selectList(wrapper);
        for (KnowledgeDocument document : documents) {
            try {
                // 更新文档状态为处理中
                document.setStatus(0);
                documentMapper.updateById(document);
            } catch (Exception e) {
                log.error("重建索引失败: documentId={}", document.getId(), e);
            }
        }
        log.info("索引重建任务已提交，共 {} 个文档", documents.size());
    }
}
