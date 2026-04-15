package cn.sdh.backend.service;

import cn.sdh.backend.dto.ChangePasswordRequest;
import cn.sdh.backend.dto.UpdateProfileRequest;
import cn.sdh.backend.dto.UserManageRequest;
import cn.sdh.backend.dto.UserStatsResponse;
import cn.sdh.backend.entity.User;
import com.baomidou.mybatisplus.core.metadata.IPage;

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

    /**
     * 分页查询用户列表
     * @param page 页码
     * @param pageSize 每页大小
     * @param keyword 关键词
     * @param status 状态
     * @param role 角色
     * @return 分页结果
     */
    IPage<User> getPage(Integer page, Integer pageSize, String keyword, Integer status, String role);

    /**
     * 创建用户
     * @param request 用户创建请求
     * @return 是否成功
     */
    boolean createUser(UserManageRequest request);

    /**
     * 更新用户
     * @param id 用户ID
     * @param request 用户更新请求
     * @return 是否成功
     */
    boolean updateUser(Long id, UserManageRequest request);

    /**
     * 删除用户
     * @param id 用户ID
     * @return 是否成功
     */
    boolean deleteUser(Long id);

    /**
     * 批量删除用户
     * @param ids 用户ID列表
     * @return 是否成功
     */
    boolean batchDeleteUsers(Long[] ids);

    /**
     * 重置用户密码
     * @param id 用户ID
     * @return 新密码
     */
    String resetPassword(Long id);

    /**
     * 切换用户状态
     * @param id 用户ID
     * @return 是否成功
     */
    boolean toggleStatus(Long id);

    /**
     * 更新个人资料
     * @param userId 用户ID
     * @param request 更新请求
     * @return 是否成功
     */
    boolean updateProfile(Long userId, UpdateProfileRequest request);

    /**
     * 修改密码
     * @param userId 用户ID
     * @param request 修改密码请求
     * @return 是否成功
     */
    boolean changePassword(Long userId, ChangePasswordRequest request);

    /**
     * 更新头像
     * @param userId 用户ID
     * @param avatarUrl 头像URL
     * @return 是否成功
     */
    boolean updateAvatar(Long userId, String avatarUrl);

    /**
     * 获取用户统计数据
     * @param userId 用户ID
     * @return 统计数据
     */
    UserStatsResponse getUserStats(Long userId);
}
