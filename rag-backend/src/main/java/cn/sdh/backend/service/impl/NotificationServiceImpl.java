package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.Notification;
import cn.sdh.backend.mapper.NotificationMapper;
import cn.sdh.backend.service.NotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification> implements NotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    public IPage<Notification> getUserNotifications(Long userId, Integer page, Integer pageSize, String type, Integer isRead) {
        Page<Notification> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        
        wrapper.eq(Notification::getUserId, userId);
        if (StringUtils.hasText(type)) {
            wrapper.eq(Notification::getType, type);
        }
        if (isRead != null) {
            wrapper.eq(Notification::getIsRead, isRead);
        }
        wrapper.orderByDesc(Notification::getCreateTime);
        
        return page(pageParam, wrapper);
    }

    @Override
    public int getUnreadCount(Long userId) {
        return notificationMapper.countUnread(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long id) {
        Notification notification = getById(id);
        if (notification != null) {
            notification.setIsRead(1);
            notification.setReadTime(LocalDateTime.now());
            updateById(notification);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead(Long userId) {
        notificationMapper.markAllAsRead(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendNotification(Long userId, String title, String content, String type, String relatedType, Long relatedId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setRelatedType(relatedType);
        notification.setRelatedId(relatedId);
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());
        save(notification);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendNotificationToUsers(List<Long> userIds, String title, String content, String type) {
        for (Long userId : userIds) {
            sendNotification(userId, title, content, type, null, null);
        }
    }
}