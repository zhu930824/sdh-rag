package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_job")
public class PlatformJob {
    public static final String PENDING = "PENDING";
    public static final String RUNNING = "RUNNING";
    public static final String RETRY_WAIT = "RETRY_WAIT";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";
    public static final String DEAD_LETTER = "DEAD_LETTER";
    public static final String CANCEL_REQUESTED = "CANCEL_REQUESTED";
    public static final String CANCELLED = "CANCELLED";

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("job_key") private String jobKey;
    @TableField("job_type") private String jobType;
    @TableField("resource_type") private String resourceType;
    @TableField("resource_id") private Long resourceId;
    @TableField("payload_json") private String payloadJson;
    private String status;
    private Integer priority;
    private Integer attempts;
    @TableField("max_attempts") private Integer maxAttempts;
    @TableField("available_at") private LocalDateTime availableAt;
    @TableField("lease_owner") private String leaseOwner;
    @TableField("lease_until") private LocalDateTime leaseUntil;
    @TableField("heartbeat_at") private LocalDateTime heartbeatAt;
    @TableField("trace_id") private String traceId;
    @TableField("created_by") private Long createdBy;
    @TableField("result_summary") private String resultSummary;
    @TableField("error_message") private String errorMessage;
    @TableField("started_at") private LocalDateTime startedAt;
    @TableField("finished_at") private LocalDateTime finishedAt;
    @TableField("create_time") private LocalDateTime createTime;
    @TableField("update_time") private LocalDateTime updateTime;
}
