package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库分块实体
 * 用于跟踪文档分块与向量数据库的对应关系
 * 采用独立存储方案：每个知识库独立存储自己的切分版本
 */
@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文档ID
     */
    @TableField("document_id")
    private Long documentId;

    /**
     * 知识库ID（独立存储方案）
     */
    @TableField("knowledge_id")
    private Long knowledgeId;

    /**
     * 分块索引
     */
    @TableField("chunk_index")
    private Integer chunkIndex;


    @TableField("chunk_id")
    private Long chunkId;

    /**
     * 分块内容
     */
    private String content;

    /**
     * 分块大小
     */
    @TableField("chunk_size")
    private Integer chunkSize;

    /**
     * Elasticsearch 向量ID
     */
    @TableField("vector_id")
    private String vectorId;

    /**
     * 关联的知识库ID列表（JSON数组）
     * @deprecated 使用 knowledgeId 字段代替
     */
    @TableField("knowledge_ids")
    @Deprecated
    private String knowledgeIds;

    /**
     * 元数据（JSON格式）
     */
    @TableField("metadata")
    private String metadata;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
