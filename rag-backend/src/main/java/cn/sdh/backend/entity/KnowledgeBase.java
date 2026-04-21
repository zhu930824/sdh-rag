package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库实体
 */
@Data
@TableName("knowledge_base")
public class KnowledgeBase implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    @TableField("user_id")
    private Long userId;

    /**
     * 分块大小（字符数）
     */
    @TableField("chunk_size")
    private Integer chunkSize;

    /**
     * 分块重叠大小
     */
    @TableField("chunk_overlap")
    private Integer chunkOverlap;

    /**
     * 嵌入模型
     */
    @TableField("embedding_model")
    private String embeddingModel;

    /**
     * 状态：0-禁用，1-启用
     */
    private Integer status;

    /**
     * 是否公开
     */
    @TableField("is_public")
    private Boolean isPublic;

    /**
     * 图标
     */
    private String icon;

    /**
     * 颜色
     */
    private String color;

    /**
     * 重排序模型
     */
    @TableField("rank_model")
    private String rankModel;

    /**
     * 是否启用多轮对话改写
     */
    @TableField("enable_rewrite")
    private Boolean enableRewrite;

    /**
     * 相似度阈值
     */
    @TableField("similarity_threshold")
    private Double similarityThreshold;

    /**
     * 关键字检索TopK
     */
    @TableField("keyword_top_k")
    private Integer keywordTopK;

    /**
     * 向量检索TopK
     */
    @TableField("vector_top_k")
    private Integer vectorTopK;

    /**
     * 关键字权重
     */
    @TableField("keyword_weight")
    private Double keywordWeight;

    /**
     * 向量权重
     */
    @TableField("vector_weight")
    private Double vectorWeight;

    /**
     * 是否启用查询扩展
     */
    @TableField("enable_query_expansion")
    private Boolean enableQueryExpansion;

    /**
     * 查询扩展数量（生成几个扩展查询）
     */
    @TableField("query_expansion_count")
    private Integer queryExpansionCount;

    /**
     * 是否启用 HyDE（假设性文档嵌入）
     */
    @TableField("enable_hyde")
    private Boolean enableHyde;

    /**
     * HyDE 生成文档使用的模型（为空则使用默认聊天模型）
     */
    @TableField("hyde_model")
    private String hydeModel;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
