package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("document_chunk")
public class DocumentChunk implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("document_id")
    private Long documentId;

    @TableField("knowledge_id")
    private Long knowledgeId;

    @TableField("chunk_index")
    private Integer chunkIndex;

    private String content;

    @TableField("chunk_size")
    private Integer chunkSize;

    private String embedding;

    private String keywords;

    @TableField("category_id")
    private Long categoryId;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
