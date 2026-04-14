package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.ChatHistory;
import cn.sdh.backend.mapper.ChatHistoryMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentMapper;
import cn.sdh.backend.mapper.TokenUsageMapper;
import cn.sdh.backend.mapper.UserMapper;
import cn.sdh.backend.service.TokenUsageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final KnowledgeDocumentMapper documentMapper;
    private final ChatHistoryMapper chatHistoryMapper;
    private final UserMapper userMapper;
    private final TokenUsageService tokenUsageService;
    private final TokenUsageMapper tokenUsageMapper;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> result = new HashMap<>();

        // 今日和昨日时间范围
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime yesterdayStart = LocalDate.now().minusDays(1).atStartOfDay();

        // 总文档数和趋势
        Long totalDocuments = documentMapper.selectCount(null);
        Long todayDocuments = documentMapper.selectCount(
            new LambdaQueryWrapper<cn.sdh.backend.entity.KnowledgeDocument>()
                .ge(cn.sdh.backend.entity.KnowledgeDocument::getCreateTime, todayStart)
        );
        Long yesterdayDocuments = documentMapper.selectCount(
            new LambdaQueryWrapper<cn.sdh.backend.entity.KnowledgeDocument>()
                .ge(cn.sdh.backend.entity.KnowledgeDocument::getCreateTime, yesterdayStart)
                .lt(cn.sdh.backend.entity.KnowledgeDocument::getCreateTime, todayStart)
        );
        result.put("totalDocuments", totalDocuments);
        result.put("documentTrend", calculateTrend(todayDocuments, yesterdayDocuments));

        // 总用户数和趋势
        Long totalUsers = userMapper.selectCount(null);
        Long todayUsers = userMapper.selectCount(
            new LambdaQueryWrapper<cn.sdh.backend.entity.User>()
                .ge(cn.sdh.backend.entity.User::getCreateTime, todayStart)
        );
        Long yesterdayUsers = userMapper.selectCount(
            new LambdaQueryWrapper<cn.sdh.backend.entity.User>()
                .ge(cn.sdh.backend.entity.User::getCreateTime, yesterdayStart)
                .lt(cn.sdh.backend.entity.User::getCreateTime, todayStart)
        );
        result.put("totalUsers", totalUsers);
        result.put("userTrend", calculateTrend(todayUsers, yesterdayUsers));

        // 总对话数和趋势
        Long totalChats = chatHistoryMapper.selectCount(null);
        Long todayChats = chatHistoryMapper.selectCount(
            new LambdaQueryWrapper<ChatHistory>()
                .ge(ChatHistory::getCreateTime, todayStart)
        );
        Long yesterdayChats = chatHistoryMapper.selectCount(
            new LambdaQueryWrapper<ChatHistory>()
                .ge(ChatHistory::getCreateTime, yesterdayStart)
                .lt(ChatHistory::getCreateTime, todayStart)
        );
        result.put("totalChats", totalChats);
        result.put("chatTrend", calculateTrend(todayChats, yesterdayChats));

        // 今日对话和趋势（今日对比昨日）
        result.put("todayChats", todayChats);
        result.put("todayChatsTrend", calculateTrend(todayChats, yesterdayChats));

        return Result.success(result);
    }

    /**
     * 计算同比变化百分比
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

    @GetMapping("/chat-trend")
    public Result<List<Map<String, Object>>> chatTrend(
            @RequestParam String startDate,
            @RequestParam String endDate) {

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        List<Map<String, Object>> trend = new ArrayList<>();

        LocalDate current = start;
        while (!current.isAfter(end)) {
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", current.toString());

            LocalDateTime dayStart = current.atStartOfDay();
            LocalDateTime dayEnd = current.plusDays(1).atStartOfDay();

            Long count = chatHistoryMapper.selectCount(
                new LambdaQueryWrapper<ChatHistory>()
                    .ge(ChatHistory::getCreateTime, dayStart)
                    .lt(ChatHistory::getCreateTime, dayEnd)
            );
            dayData.put("count", count);

            trend.add(dayData);
            current = current.plusDays(1);
        }

        return Result.success(trend);
    }

    @GetMapping("/api-cost")
    public Result<Map<String, Object>> apiCostStats(
            @RequestParam String startDate,
            @RequestParam String endDate) {

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay();

        // 获取历史累计统计
        Map<String, Object> globalStats = tokenUsageMapper.getGlobalStats();

        // 获取日期范围内的统计
        Map<String, Object> rangeStats = tokenUsageMapper.getGlobalStatsByDateRange(startTime, endTime);

        // 获取今日统计
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().plusDays(1).atStartOfDay();
        Map<String, Object> todayStats = tokenUsageMapper.getGlobalStatsByDateRange(todayStart, todayEnd);

        // 获取按模型统计（时间范围内）
        List<Map<String, Object>> modelStatsList = tokenUsageMapper.getGlobalStatsByModel(startTime, endTime);

        // 历史累计
        long totalTokens = getLongValue(globalStats, "totalTokens");
        BigDecimal totalCost = calculateCostByTokens(totalTokens);

        // 时间范围内
        long rangeTokens = getLongValue(rangeStats, "totalTokens");
        BigDecimal rangeCost = calculateCostByTokens(rangeTokens);

        // 今日
        long todayTokens = getLongValue(todayStats, "totalTokens");
        BigDecimal todayCost = calculateCostByTokens(todayTokens);

        Map<String, Object> result = new HashMap<>();
        result.put("totalTokens", totalTokens);
        result.put("totalCost", totalCost);
        result.put("rangeTokens", rangeTokens);
        result.put("rangeCost", rangeCost);
        result.put("todayTokens", todayTokens);
        result.put("todayCost", todayCost);

        // 转换模型统计格式
        List<Map<String, Object>> modelStatsResult = new ArrayList<>();
        for (Map<String, Object> modelStat : modelStatsList) {
            Map<String, Object> stat = new HashMap<>();
            stat.put("model", modelStat.get("modelName"));
            stat.put("tokens", getLongValue(modelStat, "totalTokens"));
            stat.put("cost", calculateModelCost(modelStat));
            modelStatsResult.add(stat);
        }
        result.put("modelStats", modelStatsResult);

        return Result.success(result);
    }

    /**
     * 获取数据库中的全局 Token 统计
     */
    @GetMapping("/token-usage/global")
    public Result<Map<String, Object>> globalTokenUsage() {
        Map<String, Object> stats = tokenUsageService.getGlobalStats();
        return Result.success(stats);
    }

    /**
     * 获取用户的 Token 统计
     */
    @GetMapping("/token-usage/user/{userId}")
    public Result<Map<String, Object>> userTokenUsage(@PathVariable Long userId) {
        Map<String, Object> stats = tokenUsageService.getUserTotalStats(userId);
        return Result.success(stats);
    }

    /**
     * 按日期统计 Token 使用
     */
    @GetMapping("/token-usage/by-date")
    public Result<List<Map<String, Object>>> tokenUsageByDate(
            @RequestParam Long userId,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay();

        List<Map<String, Object>> stats = tokenUsageService.statsByDate(userId, startTime, endTime);
        return Result.success(stats);
    }

    /**
     * 按模型统计 Token 使用
     */
    @GetMapping("/token-usage/by-model")
    public Result<List<Map<String, Object>>> tokenUsageByModel(
            @RequestParam Long userId,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay();

        List<Map<String, Object>> stats = tokenUsageService.statsByModel(userId, startTime, endTime);
        return Result.success(stats);
    }

    // ========== 辅助方法 ==========

    private long getLongValue(Map<String, Object> map, String key) {
        if (map == null || map.get(key) == null) {
            return 0L;
        }
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    /**
     * 计算费用
     * 价格：6元/百万token
     */
    private BigDecimal calculateCostByTokens(long totalTokens) {
        // 6元/百万token = 0.000006元/token
        BigDecimal pricePerToken = new BigDecimal("0.000006");
        return pricePerToken.multiply(BigDecimal.valueOf(totalTokens)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算总费用
     */
    private BigDecimal calculateCost(List<Map<String, Object>> modelStatsList) {
        long totalTokens = 0;
        for (Map<String, Object> modelStat : modelStatsList) {
            totalTokens += getLongValue(modelStat, "totalTokens");
        }
        return calculateCostByTokens(totalTokens);
    }

    /**
     * 计算今日费用
     */
    private BigDecimal calculateTodayCost(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> todayStats = tokenUsageMapper.getGlobalStatsByDateRange(startTime, endTime);
        long todayTokens = getLongValue(todayStats, "totalTokens");
        return calculateCostByTokens(todayTokens);
    }

    /**
     * 计算单个模型的费用
     * 价格：6元/百万token
     */
    private BigDecimal calculateModelCost(Map<String, Object> modelStat) {
        long totalTokens = getLongValue(modelStat, "totalTokens");
        return calculateCostByTokens(totalTokens);
    }
}
