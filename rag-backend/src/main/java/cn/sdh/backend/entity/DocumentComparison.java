package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("document_comparison")
public class DocumentComparison implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("document_id_1")
    private Long documentId1;

    @TableField("document_id_2")
    private Long documentId2;

    @TableField("user_id")
    private Long userId;

    @TableField("comparison_type")
    private String comparisonType;

    @TableField("similarity_score")
    private BigDecimal similarityScore;

    @TableField("diff_result")
    private String diffResult;

    private String summary;

    @TableField("create_time")
    private LocalDateTime createTime;
}
