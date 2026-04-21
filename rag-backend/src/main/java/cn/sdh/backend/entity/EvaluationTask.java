package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("evaluation_task")
public class EvaluationTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("knowledge_id")
    private Long knowledgeId;

    @TableField("task_name")
    private String taskName;

    @TableField("qa_count")
    private Integer qaCount;

    /**
     * 分块级命中率：精确匹配源分块的比例
     */
    @TableField("hit_rate")
    private BigDecimal hitRate;

    /**
     * 文档级命中率：检索到同一文档任意分块的比例
     */
    @TableField("doc_hit_rate")
    private BigDecimal docHitRate;

    /**
     * MRR (Mean Reciprocal Rank)：平均倒数排名
     */
    @TableField("mrr")
    private BigDecimal mrr;

    /**
     * 平均命中排名（只统计命中的）
     */
    @TableField("avg_hit_rank")
    private BigDecimal avgHitRank;

    /**
     * Top-K 命中分布JSON：{"top1":5,"top3":7,"top5":8,"top10":10}
     */
    @TableField("top_k_hits")
    private String topKHits;

    @TableField("negative_count")
    private Integer negativeCount;

    @TableField("negative_hit_rate")
    private BigDecimal negativeHitRate;

    @TableField("dataset_type")
    private String datasetType;

    /**
     * 状态: 0-待运行, 1-运行中, 2-完成, 3-失败
     */
    private Integer status;

    @TableField("config_snapshot")
    private String configSnapshot;

    @TableField("user_id")
    private Long userId;

    @TableField("error_message")
    private String errorMessage;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 知识库名称（非数据库字段，用于展示）
     */
    @TableField(exist = false)
    private String knowledgeName;
}
