package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("evaluation_qa")
public class EvaluationQa implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    private String question;

    @TableField("expected_answer")
    private String expectedAnswer;

    @TableField("source_chunk_id")
    private String sourceChunkId;

    @TableField("source_document_id")
    private Long sourceDocumentId;

    @TableField("source_chunk_content")
    private String sourceChunkContent;

    @TableField("retrieved_chunk_ids")
    private String retrievedChunkIds;

    /**
     * 分块级命中：精确匹配源分块
     */
    private Boolean hit;

    /**
     * 文档级命中：检索结果中包含同一文档的分块
     */
    @TableField("doc_hit")
    private Boolean docHit;

    @TableField("hit_rank")
    private Integer hitRank;

    @TableField("doc_hit_rank")
    private Integer docHitRank;

    @TableField("is_negative")
    private Boolean isNegative;

    @TableField("source_type")
    private String sourceType;

    @TableField("external_id")
    private String externalId;

    @TableField("create_time")
    private LocalDateTime createTime;
}
