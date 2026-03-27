package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.ApiQuota;
import cn.sdh.backend.mapper.ApiQuotaMapper;
import cn.sdh.backend.service.ApiQuotaService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiQuotaServiceImpl extends ServiceImpl<ApiQuotaMapper, ApiQuota> implements ApiQuotaService {

    private final ApiQuotaMapper apiQuotaMapper;

    @Override
    public ApiQuota getOrCreateQuota(Long userId, String quotaType) {
        ApiQuota quota = apiQuotaMapper.selectByUserAndType(userId, quotaType);
        if (quota == null) {
            quota = new ApiQuota();
            quota.setUserId(userId);
            quota.setQuotaType(quotaType);
            quota.setDailyLimit(100);
            quota.setMonthlyLimit(3000);
            quota.setDailyUsed(0);
            quota.setMonthlyUsed(0);
            quota.setResetDate(LocalDate.now());
            quota.setCreateTime(LocalDateTime.now());
            quota.setUpdateTime(LocalDateTime.now());
            save(quota);
        }

        checkAndResetQuota(quota);
        return quota;
    }

    @Override
    public List<ApiQuota> getUserQuotas(Long userId) {
        List<ApiQuota> quotas = apiQuotaMapper.selectByUserId(userId);
        for (ApiQuota quota : quotas) {
            checkAndResetQuota(quota);
        }
        return quotas;
    }

    @Override
    public boolean checkQuota(Long userId, String quotaType) {
        ApiQuota quota = getOrCreateQuota(userId, quotaType);
        return quota.getDailyUsed() < quota.getDailyLimit() && 
               quota.getMonthlyUsed() < quota.getMonthlyLimit();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consumeQuota(Long userId, String quotaType) {
        ApiQuota quota = getOrCreateQuota(userId, quotaType);
        
        if (quota.getDailyUsed() >= quota.getDailyLimit()) {
            throw new RuntimeException("今日API调用次数已达上限");
        }
        if (quota.getMonthlyUsed() >= quota.getMonthlyLimit()) {
            throw new RuntimeException("本月API调用次数已达上限");
        }

        apiQuotaMapper.incrementUsage(userId, quotaType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setQuota(Long userId, String quotaType, Integer dailyLimit, Integer monthlyLimit) {
        ApiQuota quota = getOrCreateQuota(userId, quotaType);
        if (dailyLimit != null) {
            quota.setDailyLimit(dailyLimit);
        }
        if (monthlyLimit != null) {
            quota.setMonthlyLimit(monthlyLimit);
        }
        quota.setUpdateTime(LocalDateTime.now());
        updateById(quota);
    }

    @Override
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void resetDailyQuotas() {
        log.info("开始重置每日配额...");
        apiQuotaMapper.resetDailyQuota();
        log.info("每日配额重置完成");
    }

    @Override
    @Scheduled(cron = "0 0 0 1 * ?")
    @Transactional(rollbackFor = Exception.class)
    public void resetMonthlyQuotas() {
        log.info("开始重置每月配额...");
        apiQuotaMapper.resetMonthlyQuota();
        log.info("每月配额重置完成");
    }

    @Override
    public int getRemainingDailyQuota(Long userId, String quotaType) {
        ApiQuota quota = getOrCreateQuota(userId, quotaType);
        return Math.max(0, quota.getDailyLimit() - quota.getDailyUsed());
    }

    @Override
    public int getRemainingMonthlyQuota(Long userId, String quotaType) {
        ApiQuota quota = getOrCreateQuota(userId, quotaType);
        return Math.max(0, quota.getMonthlyLimit() - quota.getMonthlyUsed());
    }

    private void checkAndResetQuota(ApiQuota quota) {
        LocalDate today = LocalDate.now();
        if (!today.equals(quota.getResetDate())) {
            quota.setDailyUsed(0);
            if (today.getMonthValue() != quota.getResetDate().getMonthValue() ||
                today.getYear() != quota.getResetDate().getYear()) {
                quota.setMonthlyUsed(0);
            }
            quota.setResetDate(today);
            quota.setUpdateTime(LocalDateTime.now());
            updateById(quota);
        }
    }
}
