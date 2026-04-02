package cn.sdh.backend.service;

import cn.sdh.backend.dto.RoleRequest;
import cn.sdh.backend.entity.Role;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 角色服务接口
 */
public interface RoleService {

    /**
     * 分页查询角色列表
     * @param page 页码
     * @param pageSize 每页大小
     * @param keyword 关键词
     * @param status 状态
     * @return 分页结果
     */
    IPage<Role> getPage(Integer page, Integer pageSize, String keyword, Integer status);

    /**
     * 获取所有启用的角色
     * @return 角色列表
     */
    List<Role> getAllEnabled();

    /**
     * 根据ID获取角色
     * @param id 角色ID
     * @return 角色实体
     */
    Role getById(Long id);

    /**
     * 创建角色
     * @param request 角色创建请求
     * @return 是否成功
     */
    boolean createRole(RoleRequest request);

    /**
     * 更新角色
     * @param id 角色ID
     * @param request 角色更新请求
     * @return 是否成功
     */
    boolean updateRole(Long id, RoleRequest request);

    /**
     * 删除角色
     * @param id 角色ID
     * @return 是否成功
     */
    boolean deleteRole(Long id);

    /**
     * 批量删除角色
     * @param ids 角色ID列表
     * @return 是否成功
     */
    boolean batchDeleteRoles(Long[] ids);

    /**
     * 切换角色状态
     * @param id 角色ID
     * @return 是否成功
     */
    boolean toggleStatus(Long id);
}
