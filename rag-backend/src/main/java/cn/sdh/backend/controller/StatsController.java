package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.ChatHistory;
import cn.sdh.backend.mapper.ChatHistoryMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentMapper;
import cn.sdh.backend.mapper.UserMapper;
import cn.sdh.backend.rag.TokenUsageAdvisor;
import cn.sdh.backend.service.TokenUsageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
    private final TokenUsageAdvisor tokenUsageAdvisor;
    private final TokenUsageService tokenUsageService;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> result = new HashMap<>();

        result.put("totalDocuments", documentMapper.selectCount(null));
        result.put("totalUsers", userMapper.selectCount(null));
        result.put("totalChats", chatHistoryMapper.selectCount(null));

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Long todayChats = chatHistoryMapper.selectCount(
            new LambdaQueryWrapper<ChatHistory>()
                .ge(ChatHistory::getCreateTime, todayStart)
        );
        result.put("todayChats", todayChats);

        return Result.success(result);
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

        Map<String, Object> result = new HashMap<>();

        result.put("totalTokens", 1000000);
        result.put("totalCost", 123.45);

        List<Map<String, Object>> modelStats = new ArrayList<>();
        result.put("modelStats", modelStats);

        return Result.success(result);
    }

    /**
     * 获取 Token 使用统计（内存快速查询）
     */
    @GetMapping("/token-usage")
    public Result<Map<String, Object>> tokenUsage() {
        Map<String, Long> stats = tokenUsageAdvisor.getStatistics();
        return Result.success(new HashMap<>(stats));
    }

    /**
     * 重置 Token 内存统计
     */
    @PostMapping("/token-usage/reset")
    public Result<Void> resetTokenUsage() {
        tokenUsageAdvisor.resetStatistics();
        return Result.success();
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
}