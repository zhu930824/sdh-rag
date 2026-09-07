package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_base_member")
public class KnowledgeBaseMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long knowledgeId;
    private Long userId;
    private String permission;
    private Long grantedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
