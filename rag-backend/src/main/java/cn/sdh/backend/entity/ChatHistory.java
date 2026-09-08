package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天历史实体
 */
@Data
@TableName("chat_history")
public class ChatHistory implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 会话ID
     */
    @TableField("session_id")
    private String sessionId;

    /**
     * 问题
     */
    private String question;

    /**
     * 回答
     */
    private String answer;

    /**
     * 引用来源（JSON格式）
     */
    private String sources;

    /** 本次回答所用知识库 */
    @TableField("knowledge_id")
    private Long knowledgeId;

    /** 本次回答所用模型(model_config.id) */
    @TableField("model_id")
    private Long modelId;

    /** 意图路由信息 JSON */
    @TableField("router_info")
    private String routerInfo;

    /** 关联 Agent 运行(agent_run.run_key) */
    @TableField("agent_run_key")
    private String agentRunKey;

    /** Agent 轨迹摘要 JSON(服务端截断) */
    @TableField("agent_trace")
    private String agentTrace;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
