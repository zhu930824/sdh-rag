package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_source")
public class KnowledgeSource {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("knowledge_id") private Long knowledgeId;
    private String name;
    @TableField("source_type") private String sourceType;
    @TableField("config_json") private String configJson;
    @TableField("credential_ref") private String credentialRef;
    @TableField("sync_cursor") private String syncCursor;
    private Integer status;
    @TableField("sync_enabled") private Integer syncEnabled;
    @TableField("sync_interval_minutes") private Integer syncIntervalMinutes;
    @TableField("next_sync_time") private LocalDateTime nextSyncTime;
    @TableField("last_sync_time") private LocalDateTime lastSyncTime;
    @TableField("last_sync_status") private String lastSyncStatus;
    @TableField("consecutive_failures") private Integer consecutiveFailures;
    @TableField("last_error") private String lastError;
    @TableField("created_by") private Long createdBy;
    @TableField("create_time") private LocalDateTime createTime;
    @TableField("update_time") private LocalDateTime updateTime;
}
