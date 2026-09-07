package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_sync_task")
public class KnowledgeSyncTask {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("source_id") private Long sourceId;
    @TableField("knowledge_id") private Long knowledgeId;
    @TableField("trigger_type") private String triggerType;
    @TableField("task_status") private String taskStatus;
    @TableField("cursor_before") private String cursorBefore;
    @TableField("cursor_after") private String cursorAfter;
    @TableField("scanned_count") private Integer scannedCount;
    @TableField("created_count") private Integer createdCount;
    @TableField("updated_count") private Integer updatedCount;
    @TableField("unchanged_count") private Integer unchangedCount;
    @TableField("deleted_count") private Integer deletedCount;
    @TableField("error_message") private String errorMessage;
    @TableField("started_by") private Long startedBy;
    @TableField("start_time") private LocalDateTime startTime;
    @TableField("finish_time") private LocalDateTime finishTime;
}
