package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("evaluation_experiment_run")
public class EvaluationExperimentRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("experiment_id") private Long experimentId;
    @TableField("trigger_type") private String triggerType;
    private String status;
    @TableField("error_message") private String errorMessage;
    @TableField("start_time") private LocalDateTime startTime;
    @TableField("complete_time") private LocalDateTime completeTime;
    @TableField("create_time") private LocalDateTime createTime;
}
