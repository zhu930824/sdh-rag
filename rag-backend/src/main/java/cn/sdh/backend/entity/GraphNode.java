package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("graph_node")
public class GraphNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String nodeType;

    private String name;

    private String description;

    private Long documentId;

    private String properties;

    private Integer weight;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}