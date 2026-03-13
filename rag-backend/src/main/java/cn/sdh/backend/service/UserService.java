package cn.sdh.backend.service;

import cn.sdh.backend.entity.User;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 用户登录
     * @param username 用户名
     * @param password 密码
     * @return Token
     */
    String login(String username, String password);

    /**
     * 用户注册
     * @param username 用户名
     * @param password 密码
     * @param nickname 昵称
     * @return 是否成功
     */
    boolean register(String username, String password, String nickname);

    /**
     * 根据ID获取用户
     * @param userId 用户ID
     * @return 用户实体
     */
    User getById(Long userId);

    /**
     * 根据用户名获取用户
     * @param username 用户名
     * @return 用户实体
     */
    User getByUsername(String username);

    /**
     * 更新用户信息
     * @param user 用户实体
     * @return 是否成功
     */
    boolean update(User user);
}
