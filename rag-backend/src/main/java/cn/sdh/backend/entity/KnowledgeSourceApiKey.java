package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_source_api_key")
public class KnowledgeSourceApiKey {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("source_id") private Long sourceId;
    @TableField("knowledge_id") private Long knowledgeId;
    private String name;
    @TableField("key_prefix") private String keyPrefix;
    @TableField("key_hash") private String keyHash;
    @TableField("rate_limit_per_minute") private Integer rateLimitPerMinute;
    private Integer status;
    @TableField("expires_at") private LocalDateTime expiresAt;
    @TableField("last_used_at") private LocalDateTime lastUsedAt;
    @TableField("created_by") private Long createdBy;
    @TableField("create_time") private LocalDateTime createTime;
    @TableField("revoke_time") private LocalDateTime revokeTime;
}
