package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.KnowledgeBaseTag;
import cn.sdh.backend.mapper.KnowledgeBaseTagMapper;
import cn.sdh.backend.service.KnowledgeBaseTagService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库标签关联服务实现
 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseTagServiceImpl extends ServiceImpl<KnowledgeBaseTagMapper, KnowledgeBaseTag> implements KnowledgeBaseTagService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addTagToKnowledgeBase(Long knowledgeBaseId, Long tagId) {
        // 检查是否已存在
        LambdaQueryWrapper<KnowledgeBaseTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBaseTag::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KnowledgeBaseTag::getTagId, tagId);

        if (count(wrapper) > 0) {
            return true; // 已存在
        }

        KnowledgeBaseTag kbTag = new KnowledgeBaseTag();
        kbTag.setKnowledgeBaseId(knowledgeBaseId);
        kbTag.setTagId(tagId);
        kbTag.setCreateTime(LocalDateTime.now());

        return save(kbTag);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeTagFromKnowledgeBase(Long knowledgeBaseId, Long tagId) {
        LambdaQueryWrapper<KnowledgeBaseTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBaseTag::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KnowledgeBaseTag::getTagId, tagId);

        return remove(wrapper);
    }

    @Override
    public List<Long> getTagIdsByKnowledgeBaseId(Long knowledgeBaseId) {
        LambdaQueryWrapper<KnowledgeBaseTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBaseTag::getKnowledgeBaseId, knowledgeBaseId);

        return list(wrapper).stream()
                .map(KnowledgeBaseTag::getTagId)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setKnowledgeBaseTags(Long knowledgeBaseId, List<Long> tagIds) {
        // 删除原有标签
        LambdaQueryWrapper<KnowledgeBaseTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBaseTag::getKnowledgeBaseId, knowledgeBaseId);
        remove(wrapper);

        // 添加新标签
        if (tagIds != null && !tagIds.isEmpty()) {
            for (Long tagId : tagIds) {
                KnowledgeBaseTag kbTag = new KnowledgeBaseTag();
                kbTag.setKnowledgeBaseId(knowledgeBaseId);
                kbTag.setTagId(tagId);
                kbTag.setCreateTime(LocalDateTime.now());
                save(kbTag);
            }
        }

        return true;
    }
}
