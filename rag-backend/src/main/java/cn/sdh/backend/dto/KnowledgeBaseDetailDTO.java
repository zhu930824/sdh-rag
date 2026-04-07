package cn.sdh.backend.dto;

import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.entity.Tag;
import lombok.Data;

import java.util.List;

/**
 * 知识库详情响应DTO
 */
@Data
public class KnowledgeBaseDetailDTO {

    /**
     * 知识库基本信息
     */
    private KnowledgeBase knowledgeBase;

    /**
     * 关联的标签列表
     */
    private List<Tag> tags;

    /**
     * 文档数量
     */
    private Integer documentCount;

    /**
     * 分块数量
     */
    private Integer chunkCount;

    /**
     * 处理中的文档数量
     */
    private Integer processingCount;

    /**
     * 处理成功的文档数量
     */
    private Integer successCount;

    /**
     * 处理失败的文档数量
     */
    private Integer failedCount;
}
