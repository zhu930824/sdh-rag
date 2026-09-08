package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 运行档案（一次 Agent 任务的执行档案；resume 时以 messagesJson 重建 LLM 上下文）
 */
@Data
@TableName("agent_run")
public class AgentRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外唯一标识(UUID,去连字符) */
    @TableField("run_key")
    private String runKey;

    @TableField("session_id")
    private String sessionId;

    @TableField("user_id")
    private Long userId;

    /** chat_history 行 ID */
    @TableField("history_id")
    private Long historyId;

    /** 所用知识库 */
    @TableField("knowledge_id")
    private Long knowledgeId;

    /** model_config.id */
    @TableField("model_id")
    private Long modelId;

    private String question;

    /** RUNNING/WAITING_APPROVAL/COMPLETED/FAILED/REJECTED/CANCELLED */
    private String status;

    @TableField("max_steps")
    private Integer maxSteps;

    @TableField("step_count")
    private Integer stepCount;

    /** WAITING_APPROVAL 时的代表 action */
    @TableField("pending_action_id")
    private Long pendingActionId;

    /** 消息快照(决策续跑重建 LLM 上下文,每轮截断落盘) */
    @TableField("messages_json")
    private String messagesJson;

    /** 检索证据截断快照 */
    @TableField("evidence_text")
    private String evidenceText;

    @TableField("prompt_tokens")
    private Integer promptTokens;

    @TableField("completion_tokens")
    private Integer completionTokens;

    @TableField("total_tokens")
    private Integer totalTokens;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    // 状态常量
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_WAITING_APPROVAL = "WAITING_APPROVAL";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELLED = "CANCELLED";
}
