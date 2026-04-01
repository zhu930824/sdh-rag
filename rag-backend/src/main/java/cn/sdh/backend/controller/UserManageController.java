package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.UserManageRequest;
import cn.sdh.backend.entity.User;
import cn.sdh.backend.service.UserService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户管理控制器
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserManageController {

    private final UserService userService;

    @GetMapping("/list")
    public Result<IPage<User>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String role) {
        IPage<User> result = userService.getPage(page, pageSize, keyword, status, role);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return Result.notFound("用户不存在");
        }
        user.setPassword(null);
        return Result.success(user);
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody UserManageRequest request) {
        userService.createUser(request);
        return Result.success("创建用户成功", null);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserManageRequest request) {
        userService.updateUser(id, request);
        return Result.success("更新用户成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success("删除用户成功", null);
    }

    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestBody Map<String, Long[]> request) {
        Long[] ids = request.get("ids");
        if (ids == null || ids.length == 0) {
            return Result.paramError("请选择要删除的用户");
        }
        userService.batchDeleteUsers(ids);
        return Result.success("批量删除成功", null);
    }

    @PostMapping("/{id}/reset-password")
    public Result<Map<String, String>> resetPassword(@PathVariable Long id) {
        String newPassword = userService.resetPassword(id);
        Map<String, String> data = new HashMap<>();
        data.put("password", newPassword);
        return Result.success("密码重置成功", data);
    }

    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        userService.toggleStatus(id);
        return Result.success("状态切换成功", null);
    }
}
