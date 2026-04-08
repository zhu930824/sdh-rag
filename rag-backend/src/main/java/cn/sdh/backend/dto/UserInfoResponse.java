package cn.sdh.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户信息响应
 */
@Data
public class UserInfoResponse {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;
    private String signature;
    private String role;
    private Integer status;
    private LocalDateTime createTime;

    /**
     * 默认模型ID
     */
    private Long defaultModelId;

    /**
     * 主题偏好
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

    /**
     * 用户权限列表（菜单路径）
     */
    private List<String> permissions;
}
