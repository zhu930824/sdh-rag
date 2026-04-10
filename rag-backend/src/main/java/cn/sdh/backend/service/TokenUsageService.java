package cn.sdh.backend.service;

import cn.sdh.backend.entity.TokenUsage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Token 使用服务接口
 */
public interface TokenUsageService {

    /**
     * 保存 token 使用记录
     * @param usage token 使用记录
     */
    void save(TokenUsage usage);

    /**
     * 异步保存 token 使用记录
     * @param usage token 使用记录
     */
    void saveAsync(TokenUsage usage);

    /**
     * 按日期统计
     */
    List<Map<String, Object>> statsByDate(Long userId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 按模型统计
     */
    List<Map<String, Object>> statsByModel(Long userId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取用户总统计
     */
    Map<String, Object> getUserTotalStats(Long userId);

    /**
     * 获取全局统计
     */
    Map<String, Object> getGlobalStats();
}
