package cn.sdh.backend.service;

import cn.sdh.backend.entity.ApiQuota;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ApiQuotaService extends IService<ApiQuota> {

    ApiQuota getOrCreateQuota(Long userId, String quotaType);

    List<ApiQuota> getUserQuotas(Long userId);

    boolean checkQuota(Long userId, String quotaType);

    void consumeQuota(Long userId, String quotaType);

    void setQuota(Long userId, String quotaType, Integer dailyLimit, Integer monthlyLimit);

    void resetDailyQuotas();

    void resetMonthlyQuotas();

    int getRemainingDailyQuota(Long userId, String quotaType);

    int getRemainingMonthlyQuota(Long userId, String quotaType);
}
