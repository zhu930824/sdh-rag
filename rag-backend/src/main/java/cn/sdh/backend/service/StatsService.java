package cn.sdh.backend.service;

import cn.sdh.backend.entity.StatsDaily;
import cn.sdh.backend.entity.ApiCallLog;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface StatsService extends IService<StatsDaily> {

    Map<String, Object> getOverview();

    List<Map<String, Object>> getChatTrend(LocalDate startDate, LocalDate endDate);

    Map<String, Object> getApiCostStats(LocalDate startDate, LocalDate endDate);

    void recordApiCall(Long userId, Long modelId, String apiType, int inputTokens, int outputTokens, double cost, long duration, boolean success, String errorMsg);

    void incrementDailyStat(String statType, String metricName, long value);
}