package cn.sdh.backend.common.context;

import lombok.Data;

/**
 * 用户上下文信息
 */
@Data
public class UserContext {
    
    private Long userId;
    private String username;
    
    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    /**
     * 设置用户上下文
     */
    public static void setUserContext(UserContext context) {
        CONTEXT.set(context);
    }

    /**
     * 获取用户上下文
     */
    public static UserContext getUserContext() {
        return CONTEXT.get();
    }

    /**
     * 获取当前用户ID
     */
    public static Long getCurrentUserId() {
        UserContext context = CONTEXT.get();
        return context != null ? context.getUserId() : null;
    }

    /**
     * 获取当前用户名
     */
    public static String getCurrentUsername() {
        UserContext context = CONTEXT.get();
        return context != null ? context.getUsername() : null;
    }

    /**
     * 清除用户上下文
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
