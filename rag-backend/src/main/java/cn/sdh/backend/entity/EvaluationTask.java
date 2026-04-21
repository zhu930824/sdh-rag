package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("evaluation_task")
public class EvaluationTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("knowledge_id")
    private Long knowledgeId;

    @TableField("task_name")
    private String taskName;

    @TableField("qa_count")
    private Integer qaCount;

    @TableField("hit_rate")
    private BigDecimal hitRate;

    @TableField("mrr")
    private BigDecimal mrr;

    @TableField("avg_recall")
    private BigDecimal avgRecall;

    /**
     * 状态: 0-待运行, 1-运行中, 2-完成, 3-失败
     */
    private Integer status;

    @TableField("config_snapshot")
    private String configSnapshot;

    @TableField("user_id")
    private Long userId;

    @TableField("error_message")
    private String errorMessage;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 知识库名称（非数据库字段，用于展示）
     */
    @TableField(exist = false)
    private String knowledgeName;
}
