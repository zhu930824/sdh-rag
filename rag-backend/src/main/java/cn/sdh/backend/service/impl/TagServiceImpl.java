package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.Tag;
import cn.sdh.backend.entity.DocumentTag;
import cn.sdh.backend.mapper.TagMapper;
import cn.sdh.backend.mapper.DocumentTagMapper;
import cn.sdh.backend.service.TagService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    private final TagMapper tagMapper;
    private final DocumentTagMapper documentTagMapper;

    @Override
    public IPage<Tag> getPage(Integer page, Integer pageSize, String keyword, String category) {
        Page<Tag> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Tag::getName, keyword);
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(Tag::getCategory, category);
        }
        wrapper.orderByAsc(Tag::getSort).orderByDesc(Tag::getCreateTime);
        
        return page(pageParam, wrapper);
    }

    @Override
    public List<Tag> getAllTags() {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Tag::getStatus, 1).orderByAsc(Tag::getSort);
        return list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addDocumentTag(Long documentId, Long tagId, String source, Long userId) {
        LambdaQueryWrapper<DocumentTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentTag::getDocumentId, documentId).eq(DocumentTag::getTagId, tagId);
        
        if (documentTagMapper.selectCount(wrapper) > 0) {
            return;
        }

        DocumentTag docTag = new DocumentTag();
        docTag.setDocumentId(documentId);
        docTag.setTagId(tagId);
        docTag.setSource(source);
        docTag.setUserId(userId);
        docTag.setCreateTime(LocalDateTime.now());
        documentTagMapper.insert(docTag);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeDocumentTag(Long documentId, Long tagId) {
        LambdaQueryWrapper<DocumentTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentTag::getDocumentId, documentId).eq(DocumentTag::getTagId, tagId);
        documentTagMapper.delete(wrapper);
    }

    @Override
    public List<Tag> getDocumentTags(Long documentId) {
        LambdaQueryWrapper<DocumentTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentTag::getDocumentId, documentId);
        List<DocumentTag> docTags = documentTagMapper.selectList(wrapper);

        List<Long> tagIds = docTags.stream().map(DocumentTag::getTagId).collect(Collectors.toList());
        if (tagIds.isEmpty()) {
            return List.of();
        }

        return listByIds(tagIds);
    }

    @Override
    public Tag getByName(String name) {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Tag::getName, name);
        return getOne(wrapper);
    }
}