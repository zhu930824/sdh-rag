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

    @TableField("source_chunk_content")
    private String sourceChunkContent;

    @TableField("retrieved_chunk_ids")
    private String retrievedChunkIds;

    private Boolean hit;

    @TableField("hit_rank")
    private Integer hitRank;

    @TableField("create_time")
    private LocalDateTime createTime;
}
