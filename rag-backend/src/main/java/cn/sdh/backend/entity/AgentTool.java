package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_tool")
public class AgentTool {
    @TableId(type = IdType.AUTO) private Long id;
    private String name;
    private String code;
    private String description;
    private String endpoint;
    @TableField("http_method") private String httpMethod;
    @TableField("credential_ref") private String credentialRef;
    @TableField("auth_type") private String authType;
    @TableField("auth_header") private String authHeader;
    @TableField("risk_level") private String riskLevel;
    @TableField("approval_mode") private String approvalMode;
    @TableField("approver_id") private Long approverId;
    @TableField("timeout_seconds") private Integer timeoutSeconds;
    @TableField("allow_private_network") private Integer allowPrivateNetwork;
    @TableField("allow_insecure_http") private Integer allowInsecureHttp;
    private Integer status;
    @TableField("created_by") private Long createdBy;
    @TableField("create_time") private LocalDateTime createTime;
    @TableField("update_time") private LocalDateTime updateTime;
}
