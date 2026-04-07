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
     * 切分时使用的嵌入模型
     */
    @TableField("embedding_model")
    private String embeddingModel;

    @TableField("create_time")
    private LocalDateTime createTime;
}