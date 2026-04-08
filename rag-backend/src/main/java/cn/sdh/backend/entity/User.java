package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@TableName("user")
public class User implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String nickname;

    private String avatar;

    private String email;

    private String phone;

    private String signature;

    private String role;

    /**
     * 状态：0-禁用，1-正常
     */
    private Integer status;

    /**
     * 默认模型ID
     */
    private Long defaultModelId;

    /**
     * 主题偏好: light, dark, auto
     */
    private String theme;

    /**
     * 语言偏好
     */
    private String language;

    /**
     * 是否开启邮件通知
     */
    private Boolean emailNotification;

    /**
     * 是否开启声音提醒
     */
    private Boolean soundNotification;

    /**
     * 回复语言偏好
     */
    private String replyLanguage;

    /**
     * 用户等级
     */
    private Integer userLevel;

    /**
     * 经验值
     */
    private Integer experience;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
