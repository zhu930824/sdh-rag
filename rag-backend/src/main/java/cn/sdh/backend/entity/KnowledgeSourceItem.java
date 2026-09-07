package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_source_item")
public class KnowledgeSourceItem {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("source_id") private Long sourceId;
    @TableField("external_id") private String externalId;
    @TableField("document_id") private Long documentId;
    @TableField("source_url") private String sourceUrl;
    @TableField("source_updated_at") private LocalDateTime sourceUpdatedAt;
    @TableField("content_hash") private String contentHash;
    @TableField("version_no") private Integer versionNo;
    @TableField("item_status") private String itemStatus;
    @TableField("last_seen_time") private LocalDateTime lastSeenTime;
    @TableField("create_time") private LocalDateTime createTime;
    @TableField("update_time") private LocalDateTime updateTime;
}
