package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("graph_edge")
public class GraphEdge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sourceId;

    private Long targetId;

    private String relationType;

    private BigDecimal weight;

    private String properties;

    private LocalDateTime createTime;
}