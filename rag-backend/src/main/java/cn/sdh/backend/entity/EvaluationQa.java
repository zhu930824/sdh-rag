package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("evaluation_qa")
public class EvaluationQa implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    private String question;

    @TableField("expected_answer")
    private String expectedAnswer;

    @TableField("source_chunk_id")
    private String sourceChunkId;

    @TableField("source_document_id")
    private Long sourceDocumentId;

    @TableField("expected_document_ids") private String expectedDocumentIds;
    @TableField("relevance_judgments") private String relevanceJudgments;

    @TableField("source_chunk_content")
    private String sourceChunkContent;

    @TableField("retrieved_chunk_ids")
    private String retrievedChunkIds;

    @TableField("retrieved_document_ids") private String retrievedDocumentIds;
    @TableField("retrieved_context") private String retrievedContext;
    @TableField("actual_answer") private String actualAnswer;

    /**
     * 分块级命中：精确匹配源分块
     */
    private Boolean hit;

    /**
     * 文档级命中：检索结果中包含同一文档的分块
     */
    @TableField("doc_hit")
    private Boolean docHit;

    @TableField("hit_rank")
    private Integer hitRank;

    @TableField("doc_hit_rank")
    private Integer docHitRank;

    @TableField("recall_at_k") private java.math.BigDecimal recallAtK;
    @TableField("precision_at_k") private java.math.BigDecimal precisionAtK;
    @TableField("reciprocal_rank") private java.math.BigDecimal reciprocalRank;
    @TableField("ndcg_at_k") private java.math.BigDecimal ndcgAtK;
    @TableField("average_precision") private java.math.BigDecimal averagePrecision;
    @TableField("context_precision") private java.math.BigDecimal contextPrecision;
    @TableField("context_recall") private java.math.BigDecimal contextRecall;
    private java.math.BigDecimal faithfulness;
    @TableField("answer_relevancy") private java.math.BigDecimal answerRelevancy;
    private java.math.BigDecimal completeness;
    private java.math.BigDecimal fluency;
    @TableField("overall_score") private java.math.BigDecimal overallScore;
    @TableField("judge_reason") private String judgeReason;
    @TableField("failure_category") private String failureCategory;
    @TableField("failure_suggestion") private String failureSuggestion;

    @TableField("is_negative")
    private Boolean isNegative;

    @TableField("source_type")
    private String sourceType;

    @TableField("external_id")
    private String externalId;

    private String category;
    private String difficulty;

    @TableField("business_line")
    private String businessLine;

    @TableField("user_role")
    private String userRole;

    @TableField("human_review_status")
    private String humanReviewStatus;

    @TableField("human_faithfulness")
    private java.math.BigDecimal humanFaithfulness;

    @TableField("human_answer_relevancy")
    private java.math.BigDecimal humanAnswerRelevancy;

    @TableField("human_completeness")
    private java.math.BigDecimal humanCompleteness;

    @TableField("human_fluency")
    private java.math.BigDecimal humanFluency;

    @TableField("human_overall_score")
    private java.math.BigDecimal humanOverallScore;

    @TableField("human_review_note")
    private String humanReviewNote;

    @TableField("human_reviewer_id")
    private Long humanReviewerId;

    @TableField("human_review_time")
    private LocalDateTime humanReviewTime;

    @TableField("issue_status")
    private String issueStatus;

    @TableField("issue_note")
    private String issueNote;

    @TableField("resolved_by")
    private Long resolvedBy;

    @TableField("resolved_time")
    private LocalDateTime resolvedTime;

    @TableField("create_time")
    private LocalDateTime createTime;
}
