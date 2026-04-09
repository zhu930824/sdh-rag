package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.RoleRequest;
import cn.sdh.backend.entity.Role;
import cn.sdh.backend.service.RoleService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色管理控制器
 */
@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/list")
    public Result<IPage<Role>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        IPage<Role> result = roleService.getPage(page, pageSize, keyword, status);
        return Result.success(result);
    }

    @GetMapping("/all")
    public Result<List<Role>> all() {
        List<Role> roles = roleService.getAllEnabled();
        return Result.success(roles);
    }

    @GetMapping("/{id}")
    public Result<Role> getById(@PathVariable Long id) {
        Role role = roleService.getById(id);
        if (role == null) {
            return Result.notFound("角色不存在");
        }
        return Result.success(role);
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody RoleRequest request) {
        try {
            roleService.createRole(request);
            return Result.success("创建角色成功", null);
        } catch (RuntimeException e) {
            return Result.paramError(e.getMessage());
        }
    }

    @PostMapping("/update/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        try {
            roleService.updateRole(id, request);
            return Result.success("更新角色成功", null);
        } catch (RuntimeException e) {
            return Result.paramError(e.getMessage());
        }
    }

    @PostMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            roleService.deleteRole(id);
            return Result.success("删除角色成功", null);
        } catch (RuntimeException e) {
            return Result.paramError(e.getMessage());
        }
    }

    @PostMapping("/batch-delete")
    public Result<Map<String, Integer>> batchDelete(@RequestBody Map<String, Long[]> request) {
        Long[] ids = request.get("ids");
        if (ids == null || ids.length == 0) {
            return Result.paramError("请选择要删除的角色");
        }

        int successCount = 0;
        int failCount = 0;
        for (Long id : ids) {
            try {
                roleService.deleteRole(id);
                successCount++;
            } catch (RuntimeException e) {
                failCount++;
            }
        }

        Map<String, Integer> data = new HashMap<>();
        data.put("success", successCount);
        data.put("fail", failCount);
        return Result.success("批量删除完成", data);
    }

    @PostMapping("/status/{id}")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        try {
            roleService.toggleStatus(id);
            return Result.success("状态切换成功", null);
        } catch (RuntimeException e) {
            return Result.paramError(e.getMessage());
        }
    }
}
