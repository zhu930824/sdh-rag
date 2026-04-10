package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Token 使用记录实体
 */
@Data
@TableName("token_usage")
public class TokenUsage implements Serializable {

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
     * 模型ID
     */
    @TableField("model_id")
    private Long modelId;

    /**
     * 模型名称
     */
    @TableField("model_name")
    private String modelName;

    /**
     * 模型提供者
     */
    @TableField("provider")
    private String provider;

    /**
     * 输入 token 数
     */
    @TableField("prompt_tokens")
    private Long promptTokens;

    /**
     * 输出 token 数
     */
    @TableField("completion_tokens")
    private Long completionTokens;

    /**
     * 总 token 数
     */
    @TableField("total_tokens")
    private Long totalTokens;

    /**
     * 请求耗时（毫秒）
     */
    @TableField("duration_ms")
    private Long durationMs;

    /**
     * 请求状态：success/failed
     */
    @TableField("status")
    private String status;

    /**
     * 错误信息
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 请求ID（用于追踪）
     */
    @TableField("request_id")
    private String requestId;

    /**
     * 知识库ID
     */
    @TableField("knowledge_id")
    private Long knowledgeId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}
