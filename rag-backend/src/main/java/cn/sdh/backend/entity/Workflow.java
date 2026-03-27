package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workflow")
public class Workflow {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String flowData;

    private Integer status;

    private Long userId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}