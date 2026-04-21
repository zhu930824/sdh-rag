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

    /**
     * 重排序模型
     */
    private String rankModel;

    /**
     * 是否启用多轮对话改写
     */
    private Boolean enableRewrite;

    /**
     * 相似度阈值
     */
    private Double similarityThreshold;

    /**
     * 关键字检索TopK
     */
    private Integer keywordTopK;

    /**
     * 向量检索TopK
     */
    private Integer vectorTopK;

    /**
     * 关键字权重
     */
    private Double keywordWeight;

    /**
     * 向量权重
     */
    private Double vectorWeight;

    /**
     * 是否启用查询扩展
     */
    private Boolean enableQueryExpansion;

    /**
     * 查询扩展数量
     */
    private Integer queryExpansionCount;

    /**
     * 是否启用 HyDE（假设性文档嵌入）
     */
    private Boolean enableHyde;

    /**
     * HyDE 生成文档使用的模型
     */
    private String hydeModel;

}
