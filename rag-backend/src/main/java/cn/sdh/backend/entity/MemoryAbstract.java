package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记忆摘要实体
 */
@Data
@TableName(value = "memory_abstract", autoResultMap = true)
public class MemoryAbstract {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sessionId;

    private String episodeId;

    private String topicTag;

    private String summary;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> keyPoints;

    private Integer importance;

    private Integer accessCount;

    private LocalDateTime lastAccessAt;

    private Boolean needsSummary;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
