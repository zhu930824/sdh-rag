package cn.sdh.backend.service.impl;

import cn.sdh.backend.dto.RoleRequest;
import cn.sdh.backend.entity.Role;
import cn.sdh.backend.entity.User;
import cn.sdh.backend.mapper.RoleMapper;
import cn.sdh.backend.mapper.UserMapper;
import cn.sdh.backend.service.RoleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色服务实现
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final UserMapper userMapper;

    @Override
    public IPage<Role> getPage(Integer page, Integer pageSize, String keyword, Integer status) {
        Page<Role> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Role::getName, keyword)
                    .or()
                    .like(Role::getCode, keyword)
                    .or()
                    .like(Role::getDescription, keyword));
        }

        if (status != null) {
            wrapper.eq(Role::getStatus, status);
        }

        wrapper.orderByDesc(Role::getCreateTime);
        return roleMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public List<Role> getAllEnabled() {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getStatus, 1);
        wrapper.orderByAsc(Role::getName);
        return roleMapper.selectList(wrapper);
    }

    @Override
    public Role getById(Long id) {
        return roleMapper.selectById(id);
    }

    @Override
    public boolean createRole(RoleRequest request) {
        // 检查编码是否已存在
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getCode, request.getCode());
        if (roleMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("角色编码已存在");
        }

        Role role = new Role();
        role.setName(request.getName());
        role.setCode(request.getCode());
        role.setDescription(request.getDescription());
        role.setPermissions(request.getPermissions() != null ? String.join(",", request.getPermissions()) : "");
        role.setStatus(request.getStatus());
        role.setCreateTime(LocalDateTime.now());
        return roleMapper.insert(role) > 0;
    }

        @Override
        public boolean updateRole (Long id, RoleRequest request){
            Role role = roleMapper.selectById(id);
            if (role == null) {
                throw new RuntimeException("角色不存在");
            }

            // 检查编码是否被其他角色使用
            LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Role::getCode, request.getCode());
            wrapper.ne(Role::getId, id);
            if (roleMapper.selectCount(wrapper) > 0) {
                throw new RuntimeException("角色编码已被其他角色使用");
            }

            role.setName(request.getName());
            role.setCode(request.getCode());
            role.setDescription(request.getDescription());
            role.setPermissions(request.getPermissions() != null ? String.join(",", request.getPermissions()) : "");
            role.setStatus(request.getStatus());
            return roleMapper.updateById(role) > 0;
        }

    @Override
    public boolean deleteRole(Long id) {
        return false;
    }

    @Override
    public boolean batchDeleteRoles(Long[] ids) {
        return false;
    }

    @Override
    public boolean toggleStatus(Long id) {
        return false;
    }
}





