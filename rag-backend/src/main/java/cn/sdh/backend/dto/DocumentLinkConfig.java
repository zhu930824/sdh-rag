package cn.sdh.backend.dto;

import lombok.Data;

/**
 * 文档关联配置DTO
 * 用于关联文档时设置单个文档的切分策略
 */
@Data
public class DocumentLinkConfig {

    /**
     * 文档ID
     */
    private Long documentId;

    /**
     * 分块大小（字符数）
     * 可选，不设置则使用知识库默认值
     */
    private Integer chunkSize;

    /**
     * 分块重叠大小
     * 可选，不设置则使用知识库默认值
     */
    private Integer chunkOverlap;

    /**
     * 嵌入模型
     * 可选，不设置则使用知识库默认值
     */
    private String embeddingModel;
}
