package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.Notification;
import cn.sdh.backend.service.NotificationService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/list")
    public Result<IPage<Notification>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer isRead) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        
        return Result.success(notificationService.getUserNotifications(userId, page, pageSize, type, isRead));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("count", notificationService.getUnreadCount(userId));
        return Result.success(result);
    }

    @PutMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable Long id) {
        Notification notification = notificationService.getById(id);
        if (notification == null) {
            return Result.notFound("通知不存在");
        }
        
        Long userId = UserContext.getCurrentUserId();
        if (!userId.equals(notification.getUserId())) {
            return Result.forbidden("无权操作");
        }
        
        notificationService.markAsRead(id);
        return Result.success();
    }

    @PutMapping("/read-all")
    public Result<Void> markAllAsRead() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        
        notificationService.markAllAsRead(userId);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        
        Notification notification = notificationService.getById(id);
        if (notification != null && userId.equals(notification.getUserId())) {
            notificationService.removeById(id);
        }
        return Result.success();
    }
}
