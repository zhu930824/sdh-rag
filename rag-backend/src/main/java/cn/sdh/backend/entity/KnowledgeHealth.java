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
@TableName("knowledge_health")
public class KnowledgeHealth implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("knowledge_id")
    private Long knowledgeId;

    private String checkType;

    private BigDecimal score;

    private String details;

    private String suggestions;

    @TableField("check_time")
    private LocalDateTime checkTime;
}
