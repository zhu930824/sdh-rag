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

    // ==================== RAG 优化新增配置 ====================

    /** 是否启用摘要索引 */
    @TableField("enable_summary_index")
    private Boolean enableSummaryIndex;

    /** 摘要粗筛返回文档数 */
    @TableField("summary_coarse_top_k")
    private Integer summaryCoarseTopK;

    /** 是否启用冷热分离 */
    @TableField("enable_cold_archive")
    private Boolean enableColdArchive;

    /** 热库最低结果数（不足触发冷库） */
    @TableField("hot_min_results")
    private Integer hotMinResults;

    /** MMR 相关性权重（0-1，越大越偏向相关性） */
    @TableField("mmr_lambda")
    private Double mmrLambda;

    /** 时间衰减系数（0-1，越大新鲜度影响越大） */
    @TableField("recency_alpha")
    private Double recencyAlpha;

    /** 粗筛返回文档数 */
    @TableField("coarse_top_k")
    private Integer coarseTopK;

    /** 精排返回 chunk 数 */
    @TableField("fine_top_k")
    private Integer fineTopK;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
