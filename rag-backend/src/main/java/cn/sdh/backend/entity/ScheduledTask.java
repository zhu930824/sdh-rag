package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("scheduled_task")
public class ScheduledTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String taskType;

    @TableField("cron_expression")
    private String cronExpression;

    private String params;

    private String description;

    private Byte status;

    @TableField("last_execute_time")
    private LocalDateTime lastExecuteTime;

    @TableField("next_execute_time")
    private LocalDateTime nextExecuteTime;

    @TableField("last_result")
    private String lastResult;

    @TableField("success_count")
    private Integer successCount;

    @TableField("fail_count")
    private Integer failCount;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
