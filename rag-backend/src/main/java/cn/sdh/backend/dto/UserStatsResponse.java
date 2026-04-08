package cn.sdh.backend.dto;

import lombok.Data;

/**
 * 用户统计数据响应
 */
@Data
public class UserStatsResponse {

    /**
     * 创建的知识库数量
     */
    private Long knowledgeCount;

    /**
     * 上传的文档数量
     */
    private Long documentCount;

    /**
     * 对话次数
     */
    private Long chatCount;

    /**
     * 今日对话次数
     */
    private Long todayChatCount;

    /**
     * 创建的工作流数量
     */
    private Long workflowCount;

    /**
     * 创建的提示词数量
     */
    private Long promptCount;

    /**
     * 用户等级 (根据活跃度计算)
     */
    private Integer userLevel;

    /**
     * 用户经验值
     */
    private Integer experience;
}
