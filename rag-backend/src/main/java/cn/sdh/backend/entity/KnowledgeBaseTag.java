package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库标签关联实体
 */
@Data
@TableName("knowledge_base_tag")
public class KnowledgeBaseTag implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    @TableField("tag_id")
    private Long tagId;

    @TableField("create_time")
    private LocalDateTime createTime;
}
