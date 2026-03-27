package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.PointsRecord;
import cn.sdh.backend.entity.PointsGoods;
import cn.sdh.backend.entity.PointsExchange;
import cn.sdh.backend.entity.UserProfile;
import cn.sdh.backend.service.PointsService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointsController {

    private final PointsService pointsService;

    @GetMapping("/profile")
    public Result<UserProfile> getProfile() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        
        return Result.success(pointsService.getOrCreateProfile(userId));
    }

    @GetMapping("/records")
    public Result<IPage<PointsRecord>> getRecords(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        
        return Result.success(pointsService.getPointsRecords(userId, page, pageSize));
    }

    @GetMapping("/goods")
    public Result<IPage<PointsGoods>> getGoods(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(pointsService.getAvailableGoods(page, pageSize));
    }

    @PostMapping("/exchange/{goodsId}")
    public Result<Void> exchange(@PathVariable Long goodsId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        
        try {
            pointsService.exchangeGoods(userId, goodsId);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/exchanges")
    public Result<IPage<PointsExchange>> getExchanges(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        
        return Result.success(pointsService.getUserExchanges(userId, page, pageSize));
    }

    @GetMapping("/balance")
    public Result<Integer> getBalance() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        
        return Result.success(pointsService.getPointsBalance(userId));
    }
}
