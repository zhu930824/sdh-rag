package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.HotwordStatsResponse;
import cn.sdh.backend.service.HotwordService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hotword")
@RequiredArgsConstructor
public class HotwordController {

    private final HotwordService hotwordService;

    @GetMapping("/stats")
    public Result<HotwordStatsResponse> getStats(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        HotwordStatsResponse stats = hotwordService.getStats(startDate, endDate);
        return Result.success(stats);
    }

    @GetMapping("/ranking")
    public Result<List<Map<String, Object>>> getRanking(
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        List<Map<String, Object>> ranking = hotwordService.getRanking(startDate, endDate, limit);
        return Result.success(ranking);
    }

    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> getTrend(
            @RequestParam String word,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        List<Map<String, Object>> trend = hotwordService.getTrend(word, startDate, endDate);
        return Result.success(trend);
    }

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> getList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        List<Map<String, Object>> list = hotwordService.getList(page, pageSize, keyword, startDate, endDate);
        return Result.success(list);
    }
}