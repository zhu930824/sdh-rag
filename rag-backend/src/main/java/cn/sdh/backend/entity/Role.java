package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("role")
public class Role {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String code;

    private String description;

    /**
     * 菜单权限，逗号分隔的菜单路径
     */
    @TableField("permissions")
    private String permissions;

    private Integer status;

    private LocalDateTime createTime;
}