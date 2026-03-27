package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("api_quota")
public class ApiQuota implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String quotaType;

    @TableField("daily_limit")
    private Integer dailyLimit;

    @TableField("monthly_limit")
    private Integer monthlyLimit;

    @TableField("daily_used")
    private Integer dailyUsed;

    @TableField("monthly_used")
    private Integer monthlyUsed;

    @TableField("reset_date")
    private LocalDate resetDate;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
