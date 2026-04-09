package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.ChatHistory;
import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.entity.User;
import cn.sdh.backend.entity.DashboardStats;
import cn.sdh.backend.mapper.ChatHistoryMapper;
import cn.sdh.backend.mapper.KnowledgeBaseMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentMapper;
import cn.sdh.backend.mapper.QaFeedbackMapper;
import cn.sdh.backend.mapper.UserMapper;
import cn.sdh.backend.entity.QaFeedback;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final ChatHistoryMapper chatHistoryMapper;
    private final UserMapper userMapper;
    private final QaFeedbackMapper qaFeedbackMapper;

    @GetMapping("/stats")
    public Result<DashboardStats> getStats() {
        DashboardStats stats = new DashboardStats();

        // 今日时间范围
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        // 昨日时间范围
        LocalDateTime yesterdayStart = LocalDate.now().minusDays(1).atStartOfDay();

        // 基础统计
        stats.setKnowledgeCount(knowledgeBaseMapper.selectCount(new LambdaQueryWrapper<KnowledgeBase>()));
        stats.setChatCount(chatHistoryMapper.selectCount(new LambdaQueryWrapper<ChatHistory>()));
        stats.setUserCount(userMapper.selectCount(new LambdaQueryWrapper<User>()));

        // 文档数量通过知识库统计
        Long docCount = documentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocument>());
        stats.setDocumentCount(docCount);

        // 文档同比昨日
        Long todayDocuments = documentMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeDocument>()
                .ge(KnowledgeDocument::getCreateTime, todayStart)
        );
        Long yesterdayDocuments = documentMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeDocument>()
                .ge(KnowledgeDocument::getCreateTime, yesterdayStart)
                .lt(KnowledgeDocument::getCreateTime, todayStart)
        );
        stats.setDocumentTrend(calculateTrend(todayDocuments, yesterdayDocuments));

        // 用户同比昨日
        Long todayUsers = userMapper.selectCount(
            new LambdaQueryWrapper<User>()
                .ge(User::getCreateTime, todayStart)
        );
        Long yesterdayUsers = userMapper.selectCount(
            new LambdaQueryWrapper<User>()
                .ge(User::getCreateTime, yesterdayStart)
                .lt(User::getCreateTime, todayStart)
        );
        stats.setUserTrend(calculateTrend(todayUsers, yesterdayUsers));

        // 问答同比昨日
        Long todayChats = chatHistoryMapper.selectCount(
            new LambdaQueryWrapper<ChatHistory>()
                .ge(ChatHistory::getCreateTime, todayStart)
        );
        Long yesterdayChats = chatHistoryMapper.selectCount(
            new LambdaQueryWrapper<ChatHistory>()
                .ge(ChatHistory::getCreateTime, yesterdayStart)
                .lt(ChatHistory::getCreateTime, todayStart)
        );
        stats.setTodayChatCount(todayChats);
        stats.setChatTrend(calculateTrend(todayChats, yesterdayChats));

        // 平均响应时间 (模拟数据，实际需要从日志或监控中获取)
        stats.setAvgResponseTime(calculateAvgResponseTime());

        // 准确率 (基于用户反馈)
        stats.setAccuracyRate(calculateAccuracyRate());

        // 过去12小时趋势
        stats.setHourlyStats(getHourlyStats());

        return Result.success(stats);
    }

    /**
     * 计算同比变化百分比
     * @param today 今日数量
     * @param yesterday 昨日数量
     * @return 变化百分比，正数表示增长，负数表示下降
     */
    private double calculateTrend(Long today, Long yesterday) {
        if (yesterday == null || yesterday == 0) {
            return (today != null && today > 0) ? 100.0 : 0.0;
        }
        if (today == null) {
            today = 0L;
        }
        return Math.round((today - yesterday) * 1000.0 / yesterday) / 10.0;
    }

    /**
     * 计算平均响应时间
     * 实际项目中应该从请求日志中计算
     */
    private Double calculateAvgResponseTime() {
        // 模拟返回，实际项目中应该统计真实的响应时间
        // 可以从 operation_log 表中计算，或者使用 AOP 记录接口耗时
        return 0.3 + Math.random() * 0.2; // 0.3-0.5秒之间
    }

    /**
     * 计算准确率
     * 基于用户反馈的点赞/点踩比例
     */
    private Double calculateAccuracyRate() {
        // 获取总反馈数
        Long totalFeedback = qaFeedbackMapper.selectCount(new LambdaQueryWrapper<QaFeedback>());

        if (totalFeedback == 0) {
            // 如果没有反馈数据，返回默认值
            return 98.5;
        }

        // 获取点赞数 (rating=1 表示点赞)
        Long likes = qaFeedbackMapper.selectCount(
                new LambdaQueryWrapper<QaFeedback>()
                        .eq(QaFeedback::getRating, 1)
        );

        // 计算准确率
        double rate = (likes.doubleValue() / totalFeedback.doubleValue()) * 100;
        // 保留一位小数
        return Math.round(rate * 10.0) / 10.0;
    }

    /**
     * 获取过去12小时每小时的问答统计
     */
    private List<DashboardStats.HourlyStats> getHourlyStats() {
        List<DashboardStats.HourlyStats> result = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:00");

        for (int i = 11; i >= 0; i--) {
            LocalDateTime hourStart = LocalDateTime.now().minusHours(i).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime hourEnd = hourStart.plusHours(1);

            Long count = chatHistoryMapper.selectCount(
                    new LambdaQueryWrapper<ChatHistory>()
                            .ge(ChatHistory::getCreateTime, hourStart)
                            .lt(ChatHistory::getCreateTime, hourEnd)
            );

            DashboardStats.HourlyStats hourlyStats = new DashboardStats.HourlyStats();
            hourlyStats.setHour(hourStart.format(formatter));
            hourlyStats.setCount(count.intValue());

            result.add(hourlyStats);
        }

        return result;
    }

    /**
     * 获取最近活动记录
     */
    @GetMapping("/recent-activities")
    public Result<List<ActivityRecord>> getRecentActivities() {
        List<ActivityRecord> activities = new ArrayList<>();

        // 获取最近的聊天记录
        List<ChatHistory> recentChats = chatHistoryMapper.selectList(
                new LambdaQueryWrapper<ChatHistory>()
                        .orderByDesc(ChatHistory::getCreateTime)
                        .last("LIMIT 5")
        );

        for (ChatHistory chat : recentChats) {
            ActivityRecord record = new ActivityRecord();
            record.setId(chat.getId());
            record.setTitle("AI问答：" + truncate(chat.getQuestion(), 30));
            record.setTime(formatTime(chat.getCreateTime()));
            record.setType("问答");
            record.setStatus("success");
            record.setStatusText("完成");
            activities.add(record);
        }

        return Result.success(activities);
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }

    private String formatTime(LocalDateTime time) {
        if (time == null) return "";
        long minutes = java.time.Duration.between(time, LocalDateTime.now()).toMinutes();
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + "分钟前";
        long hours = minutes / 60;
        if (hours < 24) return hours + "小时前";
        return time.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }

    @Data
    public static class ActivityRecord {
        private Long id;
        private String title;
        private String time;
        private String type;
        private String status;
        private String statusText;
    }
}
