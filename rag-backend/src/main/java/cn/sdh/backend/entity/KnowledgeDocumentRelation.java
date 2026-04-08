package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库文档关联实体
 */
@Data
@TableName("knowledge_document_relation")
public class KnowledgeDocumentRelation implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("knowledge_id")
    private Long knowledgeId;

    @TableField("document_id")
    private Long documentId;

    /**
     * 处理状态：0-待处理，1-处理中，2-成功，3-失败
     */
    @TableField("process_status")
    private Integer processStatus;

    /**
     * 分块数量
     */
    @TableField("chunk_count")
    private Integer chunkCount;

    /**
     * 处理时间
     */
    @TableField("process_time")
    private LocalDateTime processTime;

    /**
     * 切分策略模式：default-默认，smart-智能切分，custom-自定义
     */
    @TableField("chunk_mode")
    private String chunkMode;

    /**
     * 自定义切分方式：length-按长度，page-按页，heading-按标题，regex-按正则
     */
    @TableField("split_type")
    private String splitType;

    /**
     * 切分时使用的分块大小
     */
    @TableField("chunk_size")
    private Integer chunkSize;

    /**
     * 切分时使用的重叠大小
     */
    @TableField("chunk_overlap")
    private Integer chunkOverlap;

    /**
     * 每块页数（按页切分时使用）
     */
    @TableField("pages_per_chunk")
    private Integer pagesPerChunk;

    /**
     * 标题层级（按标题切分时使用，JSON数组格式）
     */
    @TableField("heading_levels")
    private String headingLevels;

    /**
     * 正则表达式（按正则切分时使用）
     */
    @TableField("regex_pattern")
    private String regexPattern;

    /**
     * 切分时使用的嵌入模型
     */
    @TableField("embedding_model")
    private String embeddingModel;

    /**
     * 智能切分配置（JSON格式）
     */
    @TableField("smart_config")
    private String smartConfig;

    /**
     * 处理错误信息
     */
    @TableField("error_message")
    private String errorMessage;

    @TableField("create_time")
    private LocalDateTime createTime;
}