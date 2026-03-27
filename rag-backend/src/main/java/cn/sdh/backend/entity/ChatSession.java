package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("chat_session")
public class ChatSession implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private String sessionId;

    @TableField("user_id")
    private Long userId;

    private String title;

    @TableField("model_id")
    private Long modelId;

    @TableField("prompt_template_id")
    private Long promptTemplateId;

    @TableField("message_count")
    private Integer messageCount;

    @TableField("total_tokens")
    private Integer totalTokens;

    @TableField("is_starred")
    private Byte isStarred;

    @TableField("is_archived")
    private Byte isArchived;

    @TableField("last_message_time")
    private LocalDateTime lastMessageTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
