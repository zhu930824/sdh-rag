package cn.sdh.backend.entity;

import lombok.Data;

import java.util.List;

@Data
public class DashboardStats {
    private Long knowledgeCount;
    private Long documentCount;
    private Long chatCount;
    private Long userCount;

    // 今日统计
    private Long todayChatCount;

    // 同比昨日趋势百分比
    private Double documentTrend;
    private Double userTrend;
    private Double chatTrend;

    // 性能指标
    private Double avgResponseTime;  // 平均响应时间(秒)
    private Double accuracyRate;     // 准确率(百分比)

    // 趋势数据 - 过去12小时每小时的问答数
    private List<HourlyStats> hourlyStats;

    @Data
    public static class HourlyStats {
        private String hour;      // 小时标签 如 "08:00"
        private Integer count;    // 问答数量
    }
}