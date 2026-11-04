package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_draft")
public class KnowledgeDraft {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("improvement_id") private Long improvementId;
    @TableField("knowledge_id") private Long knowledgeId;
    private String title;
    private String content;
    private String status;
    @TableField("regression_queries") private String regressionQueries;
    @TableField("published_document_id") private Long publishedDocumentId;
    @TableField(value="published_generation",insertStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private String publishedGeneration;
    @TableField("content_checksum") private String contentChecksum;
    @TableField("validation_status") private String validationStatus;
    @TableField("validation_result") private String validationResult;
    @TableField("review_note") private String reviewNote;
    @TableField("error_message") private String errorMessage;
    @TableField("created_by") private Long createdBy;
    @TableField("reviewed_by") private Long reviewedBy;
    @TableField("created_at") private LocalDateTime createdAt;
    @TableField("updated_at") private LocalDateTime updatedAt;
    @TableField("reviewed_at") private LocalDateTime reviewedAt;
    @TableField("published_at") private LocalDateTime publishedAt;
    @TableField("rolled_back_at") private LocalDateTime rolledBackAt;
}
