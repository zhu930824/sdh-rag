package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_publish_record")
public class KnowledgePublishRecord {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("draft_id") private Long draftId;
    @TableField("knowledge_id") private Long knowledgeId;
    @TableField("document_id") private Long documentId;
    private String action;
    private String status;
    private String detail;
    @TableField("operator_id") private Long operatorId;
    @TableField("created_at") private LocalDateTime createdAt;
}
