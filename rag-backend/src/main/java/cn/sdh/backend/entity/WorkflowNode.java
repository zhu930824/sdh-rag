package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workflow_node")
public class WorkflowNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowId;

    private String nodeType;

    private String nodeName;

    private String nodeConfig;

    private Integer positionX;

    private Integer positionY;

    private LocalDateTime createTime;
}