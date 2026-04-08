package cn.sdh.backend.dto;

import lombok.Data;

/**
 * 用户偏好设置请求
 */
@Data
public class UserPreferenceRequest {

    /**
     * 默认模型ID
     */
    private Long defaultModelId;

    /**
     * 主题: light, dark, auto
     */
    private String theme;

    /**
     * 语言
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
}
