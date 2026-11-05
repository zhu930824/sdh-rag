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
    @TableField(value="current_execution_id",insertStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private String currentExecutionId;

    @TableField("job_id")
    private Long jobId;

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

    @TableField("dataset_id") private Long datasetId;
    @TableField("dataset_version") private Integer datasetVersion;

    @TableField("evaluation_mode") private String evaluationMode;
    @TableField("top_k") private Integer topK;
    @TableField("labeled_count") private Integer labeledCount;
    @TableField("judged_count") private Integer judgedCount;
    @TableField("recall_at_k") private BigDecimal recallAtK;
    @TableField("precision_at_k") private BigDecimal precisionAtK;
    @TableField("ndcg_at_k") private BigDecimal ndcgAtK;
    @TableField("map_score") private BigDecimal mapScore;
    @TableField("context_precision") private BigDecimal contextPrecision;
    @TableField("context_recall") private BigDecimal contextRecall;
    private BigDecimal faithfulness;
    @TableField("answer_relevancy") private BigDecimal answerRelevancy;
    private BigDecimal completeness;
    private BigDecimal fluency;
    @TableField("overall_score") private BigDecimal overallScore;
    @TableField("judge_model") private String judgeModel;
    @TableField("baseline_task_id") private Long baselineTaskId;
    @TableField("regression_status") private String regressionStatus;

    /**
     * 状态: 0-待运行, 1-运行中, 2-完成, 3-失败, 4-已取消
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

    @TableField(exist = false)
    private Boolean canDelete;

    @TableField(exist = false)
    private String jobStatus;
}
