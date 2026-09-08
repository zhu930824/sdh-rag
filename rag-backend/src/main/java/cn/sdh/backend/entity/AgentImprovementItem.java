package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_improvement_item")
public class AgentImprovementItem {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("source_type") private String sourceType;
    @TableField("source_id") private Long sourceId;
    @TableField("run_id") private Long runId;
    @TableField("action_id") private Long actionId;
    @TableField("knowledge_id") private Long knowledgeId;
    private String category;
    private String priority;
    private String title;
    private String detail;
    private String status;
    @TableField("created_by") private Long createdBy;
    @TableField("reviewed_by") private Long reviewedBy;
    @TableField("review_note") private String reviewNote;
    @TableField("created_at") private LocalDateTime createdAt;
    @TableField("reviewed_at") private LocalDateTime reviewedAt;
    @TableField("updated_at") private LocalDateTime updatedAt;
}
