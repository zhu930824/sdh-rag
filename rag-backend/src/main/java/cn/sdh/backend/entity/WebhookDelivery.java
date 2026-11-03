package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("webhook_delivery")
public class WebhookDelivery {
    public static final String PENDING = "PENDING";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";
    public static final String DEAD_LETTER = "DEAD_LETTER";
    public static final String SKIPPED = "SKIPPED";

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("delivery_key") private String deliveryKey;
    @TableField("outbox_id") private Long outboxId;
    @TableField("webhook_id") private Long webhookId;
    @TableField("event_type") private String eventType;
    private String status;
    private Integer attempts;
    @TableField("error_message") private String errorMessage;
    @TableField("delivered_at") private LocalDateTime deliveredAt;
    @TableField("create_time") private LocalDateTime createTime;
    @TableField("update_time") private LocalDateTime updateTime;
}
