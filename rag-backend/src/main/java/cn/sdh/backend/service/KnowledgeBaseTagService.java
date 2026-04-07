package cn.sdh.backend.service;

import cn.sdh.backend.entity.KnowledgeBaseTag;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 知识库标签关联服务
 */
public interface KnowledgeBaseTagService extends IService<KnowledgeBaseTag> {

    /**
     * 添加标签到知识库
     */
    boolean addTagToKnowledgeBase(Long knowledgeBaseId, Long tagId);

    /**
     * 移除知识库的标签
     */
    boolean removeTagFromKnowledgeBase(Long knowledgeBaseId, Long tagId);

    /**
     * 获取知识库的所有标签ID
     */
    List<Long> getTagIdsByKnowledgeBaseId(Long knowledgeBaseId);

    /**
     * 设置知识库的标签（替换原有标签）
     */
    boolean setKnowledgeBaseTags(Long knowledgeBaseId, List<Long> tagIds);
}
