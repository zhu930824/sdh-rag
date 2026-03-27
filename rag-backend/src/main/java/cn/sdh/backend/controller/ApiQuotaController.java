package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.ApiQuota;
import cn.sdh.backend.service.ApiQuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quota")
@RequiredArgsConstructor
public class ApiQuotaController {

    private final ApiQuotaService apiQuotaService;

    @GetMapping("/list")
    public Result<List<ApiQuota>> list() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        return Result.success(apiQuotaService.getUserQuotas(userId));
    }

    @GetMapping("/{quotaType}")
    public Result<ApiQuota> getQuota(@PathVariable String quotaType) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        return Result.success(apiQuotaService.getOrCreateQuota(userId, quotaType));
    }

    @GetMapping("/check/{quotaType}")
    public Result<Map<String, Object>> checkQuota(@PathVariable String quotaType) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("hasQuota", apiQuotaService.checkQuota(userId, quotaType));
        result.put("remainingDaily", apiQuotaService.getRemainingDailyQuota(userId, quotaType));
        result.put("remainingMonthly", apiQuotaService.getRemainingMonthlyQuota(userId, quotaType));

        ApiQuota quota = apiQuotaService.getOrCreateQuota(userId, quotaType);
        result.put("dailyLimit", quota.getDailyLimit());
        result.put("monthlyLimit", quota.getMonthlyLimit());
        result.put("dailyUsed", quota.getDailyUsed());
        result.put("monthlyUsed", quota.getMonthlyUsed());

        return Result.success(result);
    }

    @PostMapping("/set")
    public Result<Void> setQuota(@RequestBody Map<String, Object> request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        String quotaType = (String) request.get("quotaType");
        Integer dailyLimit = (Integer) request.get("dailyLimit");
        Integer monthlyLimit = (Integer) request.get("monthlyLimit");

        apiQuotaService.setQuota(userId, quotaType, dailyLimit, monthlyLimit);
        return Result.success();
    }

    @PostMapping("/admin/set")
    public Result<Void> adminSetQuota(@RequestBody Map<String, Object> request) {
        Long userId = ((Number) request.get("userId")).longValue();
        String quotaType = (String) request.get("quotaType");
        Integer dailyLimit = (Integer) request.get("dailyLimit");
        Integer monthlyLimit = (Integer) request.get("monthlyLimit");

        apiQuotaService.setQuota(userId, quotaType, dailyLimit, monthlyLimit);
        return Result.success();
    }
}
