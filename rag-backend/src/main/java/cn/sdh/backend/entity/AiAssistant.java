package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_assistant")
public class AiAssistant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String category;

    private String icon;

    private String systemPrompt;

    private String welcomeMessage;

    private String suggestedQuestions;

    private String knowledgeIds;

    private Long modelConfigId;

    private BigDecimal temperature;

    private Integer maxTokens;

    private Integer isPublic;

    private Long creatorId;

    private Integer useCount;

    private BigDecimal ratingAvg;

    private Integer ratingCount;

    private Integer status;

    private Integer sort;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}