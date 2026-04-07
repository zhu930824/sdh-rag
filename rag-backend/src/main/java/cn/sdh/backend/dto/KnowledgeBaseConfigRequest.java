package cn.sdh.backend.dto;

import lombok.Data;

import java.util.List;

/**
 * 知识库配置更新请求
 */
@Data
public class KnowledgeBaseConfigRequest {

    /**
     * 分块大小（字符数）
     */
    private Integer chunkSize;

    /**
     * 分块重叠大小
     */
    private Integer chunkOverlap;

    /**
     * 嵌入模型
     */
    private String embeddingModel;

    /**
     * 标签ID列表
     */
    private List<Long> tagIds;
}
