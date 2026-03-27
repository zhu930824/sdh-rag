package cn.sdh.backend.service;

import cn.sdh.backend.entity.Notification;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface NotificationService extends IService<Notification> {

    IPage<Notification> getUserNotifications(Long userId, Integer page, Integer pageSize, String type, Integer isRead);

    int getUnreadCount(Long userId);

    void markAsRead(Long id);

    void markAllAsRead(Long userId);

    void sendNotification(Long userId, String title, String content, String type, String relatedType, Long relatedId);

    void sendNotificationToUsers(List<Long> userIds, String title, String content, String type);
}