package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 步骤轨迹
 */
@Data
@TableName("agent_step")
public class AgentStep {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("run_id")
    private Long runId;

    /** 全局递增,跨类型 */
    private Integer seq;

    /** llm/tool/retrieval */
    @TableField("step_type")
    private String stepType;

    @TableField("tool_code")
    private String toolCode;

    @TableField("action_id")
    private Long actionId;

    @TableField("input_json")
    private String inputJson;

    @TableField("output_json")
    private String outputJson;

    /** RUNNING/SUCCEEDED/FAILED/PENDING_APPROVAL/REJECTED */
    private String status;

    @TableField("latency_ms")
    private Integer latencyMs;

    private Integer tokens;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public static final String STEP_TYPE_LLM = "llm";
    public static final String STEP_TYPE_TOOL = "tool";
    public static final String STEP_TYPE_RETRIEVAL = "retrieval";
}
