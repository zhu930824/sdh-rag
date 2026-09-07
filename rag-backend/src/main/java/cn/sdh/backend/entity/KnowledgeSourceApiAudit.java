package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_source_api_audit")
public class KnowledgeSourceApiAudit {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("api_key_id") private Long apiKeyId;
    @TableField("source_id") private Long sourceId;
    @TableField("knowledge_id") private Long knowledgeId;
    @TableField("request_id") private String requestId;
    @TableField("item_count") private Integer itemCount;
    @TableField("task_id") private Long taskId;
    @TableField("result_status") private String resultStatus;
    @TableField("response_code") private Integer responseCode;
    @TableField("error_message") private String errorMessage;
    @TableField("duration_ms") private Long durationMs;
    @TableField("user_agent") private String userAgent;
    @TableField("create_time") private LocalDateTime createTime;
}
