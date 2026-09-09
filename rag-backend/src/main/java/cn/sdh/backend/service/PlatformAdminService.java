package cn.sdh.backend.service;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.User;
import cn.sdh.backend.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Central authorization boundary for platform-wide administration APIs.
 */
@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    private final UserMapper userMapper;

    public User requireAdmin() {
        Long actor = UserContext.getCurrentUserId();
        if (actor == null) {
            throw new BusinessException(401, "未登录或登录状态已失效");
        }

        User user = userMapper.selectById(actor);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(401, "用户不存在或已被禁用");
        }
        if (!"admin".equalsIgnoreCase(user.getRole())) {
            throw new BusinessException(403, "仅平台管理员可以执行此操作");
        }
        return user;
    }
}
