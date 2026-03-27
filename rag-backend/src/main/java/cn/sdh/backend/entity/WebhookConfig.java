package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("webhook_config")
public class WebhookConfig implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String url;

    private String secret;

    private String events;

    private String headers;

    private Byte status;

    @TableField("last_trigger_time")
    private LocalDateTime lastTriggerTime;

    @TableField("fail_count")
    private Integer failCount;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
