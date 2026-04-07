package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库实体
 */
@Data
@TableName("knowledge_base")
public class KnowledgeBase implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    @TableField("user_id")
    private Long userId;

    /**
     * 分块大小（字符数）
     */
    @TableField("chunk_size")
    private Integer chunkSize;

    /**
     * 分块重叠大小
     */
    @TableField("chunk_overlap")
    private Integer chunkOverlap;

    /**
     * 嵌入模型
     */
    @TableField("embedding_model")
    private String embeddingModel;

    /**
     * 状态：0-禁用，1-启用
     */
    private Integer status;

    /**
     * 是否公开
     */
    @TableField("is_public")
    private Boolean isPublic;

    /**
     * 图标
     */
    private String icon;

    /**
     * 颜色
     */
    private String color;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
