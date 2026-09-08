package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("evaluation_experiment_variant")
public class EvaluationExperimentVariant {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("experiment_id") private Long experimentId;
    private String name;
    @TableField("evaluation_mode") private String evaluationMode;
    @TableField("top_k") private Integer topK;
    @TableField("judge_model") private String judgeModel;
    @TableField("baseline_task_id") private Long baselineTaskId;
    @TableField("sort_order") private Integer sortOrder;
    @TableField("create_time") private LocalDateTime createTime;
}
