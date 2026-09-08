package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("test_dataset_item")
public class TestDatasetItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("dataset_id")
    private Long datasetId;

    private String question;

    @TableField("expected_answer")
    private String expectedAnswer;

    @TableField("source_chunk_id")
    private String sourceChunkId;

    @TableField("source_document_id")
    private Long sourceDocumentId;

    @TableField("expected_document_ids") private String expectedDocumentIds;
    @TableField("relevance_judgments") private String relevanceJudgments;

    @TableField("is_negative")
    private Boolean isNegative;

    @TableField("external_id")
    private String externalId;

    @TableField("source_feedback_id")
    private Long sourceFeedbackId;

    private String category;
    private String difficulty;

    @TableField("business_line")
    private String businessLine;

    @TableField("user_role")
    private String userRole;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("create_time")
    private LocalDateTime createTime;
}
