package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("prompt_template")
public class PromptTemplate implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String code;

    private String category;

    private String content;

    private String variables;

    private String description;

    @TableField("is_system")
    private Byte isSystem;

    private Byte status;

    @TableField("use_count")
    private Integer useCount;

    @TableField("user_id")
    private Long userId;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
