package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("evaluation_experiment_run_task")
public class EvaluationExperimentRunTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("run_id") private Long runId;
    @TableField("variant_id") private Long variantId;
    @TableField("task_id") private Long taskId;
    private Integer attempt;
    @TableField("create_time") private LocalDateTime createTime;
}
