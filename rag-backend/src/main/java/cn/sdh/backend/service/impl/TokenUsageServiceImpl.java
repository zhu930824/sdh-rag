package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.TokenUsage;
import cn.sdh.backend.mapper.TokenUsageMapper;
import cn.sdh.backend.service.TokenUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Token 使用服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenUsageServiceImpl implements TokenUsageService {

    private final TokenUsageMapper tokenUsageMapper;

    @Override
    public void save(TokenUsage usage) {
        if (usage.getCreateTime() == null) {
            usage.setCreateTime(LocalDateTime.now());
        }
        tokenUsageMapper.insert(usage);
        log.debug("保存 Token 使用记录: userId={}, totalTokens={}", usage.getUserId(), usage.getTotalTokens());
    }

    @Override
    @Async
    public void saveAsync(TokenUsage usage) {
        try {
            save(usage);
        } catch (Exception e) {
            log.error("异步保存 Token 使用记录失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> statsByDate(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        return tokenUsageMapper.statsByDate(userId, startTime, endTime);
    }

    @Override
    public List<Map<String, Object>> statsByModel(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        return tokenUsageMapper.statsByModel(userId, startTime, endTime);
    }

    @Override
    public Map<String, Object> getUserTotalStats(Long userId) {
        return tokenUsageMapper.getUserTotalStats(userId);
    }

    @Override
    public Map<String, Object> getGlobalStats() {
        return tokenUsageMapper.getGlobalStats();
    }
}
