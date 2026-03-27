package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("session_share")
public class SessionShare implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private String sessionId;

    @TableField("user_id")
    private Long userId;

    @TableField("share_code")
    private String shareCode;

    private String password;

    @TableField("expire_time")
    private LocalDateTime expireTime;

    @TableField("view_count")
    private Integer viewCount;

    private Byte status;

    @TableField("create_time")
    private LocalDateTime createTime;
}
