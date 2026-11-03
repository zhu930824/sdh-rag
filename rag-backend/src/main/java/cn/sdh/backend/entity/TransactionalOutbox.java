package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("transactional_outbox")
public class TransactionalOutbox {
    public static final String NEW = "NEW";
    public static final String RELAYING = "RELAYING";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String RETRY_WAIT = "RETRY_WAIT";
    public static final String DEAD_LETTER = "DEAD_LETTER";

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("event_key") private String eventKey;
    @TableField("event_type") private String eventType;
    @TableField("aggregate_type") private String aggregateType;
    @TableField("aggregate_id") private Long aggregateId;
    @TableField("payload_json") private String payloadJson;
    private String status;
    @TableField("relay_attempts") private Integer relayAttempts;
    @TableField("max_attempts") private Integer maxAttempts;
    @TableField("available_at") private LocalDateTime availableAt;
    @TableField("lease_owner") private String leaseOwner;
    @TableField("lease_until") private LocalDateTime leaseUntil;
    @TableField("trace_id") private String traceId;
    @TableField("created_by") private Long createdBy;
    @TableField("error_message") private String errorMessage;
    @TableField("published_at") private LocalDateTime publishedAt;
    @TableField("create_time") private LocalDateTime createTime;
    @TableField("update_time") private LocalDateTime updateTime;
}
