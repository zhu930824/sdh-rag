package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.HotwordExportDTO;
import cn.sdh.backend.dto.HotwordStatsResponse;
import cn.sdh.backend.service.HotwordService;
import com.alibaba.excel.EasyExcel;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
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
    public Result<Map<String, Object>> getList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        Map<String, Object> result = hotwordService.getList(page, pageSize, keyword, startDate, endDate);
        return Result.success(result);
    }

    /**
     * 导出热点词数据到 Excel
     */
    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            HttpServletResponse response) throws IOException {

        log.info("导出热点词数据, startDate={}, endDate={}, keyword={}", startDate, endDate, keyword);

        // 获取所有数据（不分页）
        Map<String, Object> result = hotwordService.getList(1, 10000, keyword, startDate, endDate);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) result.get("list");

        // 转换为导出 DTO
        List<HotwordExportDTO> exportList = IntStream.range(0, dataList.size())
                .mapToObj(i -> HotwordExportDTO.fromMap(dataList.get(i), i + 1))
                .collect(Collectors.toList());

        // 设置响应头
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");

        // 文件名编码
        String fileName = URLEncoder.encode("热点词统计", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        // 使用 EasyExcel 写入
        EasyExcel.write(response.getOutputStream(), HotwordExportDTO.class)
                .sheet("热点词")
                .doWrite(exportList);

        log.info("导出热点词数据完成, 共 {} 条", exportList.size());
    }
}
