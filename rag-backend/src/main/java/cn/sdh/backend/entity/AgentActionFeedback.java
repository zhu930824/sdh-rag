package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_action_feedback")
public class AgentActionFeedback {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("action_id") private Long actionId;
    @TableField("user_id") private Long userId;
    private String outcome;
    private String comment;
    @TableField("reusable_knowledge") private Integer reusableKnowledge;
    @TableField("create_time") private LocalDateTime createTime;
}
