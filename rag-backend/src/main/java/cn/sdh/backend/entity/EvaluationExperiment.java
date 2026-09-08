package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("evaluation_experiment")
public class EvaluationExperiment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    @TableField("knowledge_id") private Long knowledgeId;
    @TableField("dataset_id") private Long datasetId;
    private String status;
    @TableField("schedule_enabled") private Boolean scheduleEnabled;
    @TableField("cron_expression") private String cronExpression;
    @TableField("next_run_time") private LocalDateTime nextRunTime;
    @TableField("judge_budget") private Integer judgeBudget;
    @TableField("max_retries") private Integer maxRetries;
    @TableField("alert_on_regression") private Boolean alertOnRegression;
    @TableField("user_id") private Long userId;
    @TableField("last_run_time") private LocalDateTime lastRunTime;
    @TableField("create_time") private LocalDateTime createTime;
    @TableField("update_time") private LocalDateTime updateTime;
}
