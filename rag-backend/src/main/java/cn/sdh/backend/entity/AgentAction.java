package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_action")
public class AgentAction {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("workflow_id") private Long workflowId;
    @TableField("node_id") private String nodeId;
    @TableField("tool_id") private Long toolId;
    @TableField("tool_name") private String toolName;
    @TableField("requested_by") private Long requestedBy;
    @TableField("approver_id") private Long approverId;
    @TableField("idempotency_key") private String idempotencyKey;
    @TableField("request_json") private String requestJson;
    @TableField("evidence_json") private String evidenceJson;
    private String status;
    @TableField("result_json") private String resultJson;
    @TableField("response_code") private Integer responseCode;
    @TableField("error_message") private String errorMessage;
    @TableField("requested_at") private LocalDateTime requestedAt;
    @TableField("decided_at") private LocalDateTime decidedAt;
    @TableField("executed_at") private LocalDateTime executedAt;
    /** Agent run id(agent_run.id),审批 -> Agent 续跑路由 */
    @TableField("run_id") private Long runId;
    /** Agent step id(agent_step.id) */
    @TableField("step_id") private Long stepId;
    /** 工作流执行记录 id(workflow_execution.id),审批 -> workflow 续跑路由 */
    @TableField("workflow_execution_id") private Long workflowExecutionId;
    @TableField("update_time") private LocalDateTime updateTime;
}
