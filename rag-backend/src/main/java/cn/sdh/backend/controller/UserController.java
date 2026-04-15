package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.ChangePasswordRequest;
import cn.sdh.backend.dto.LoginRequest;
import cn.sdh.backend.dto.RegisterRequest;
import cn.sdh.backend.dto.UpdateProfileRequest;
import cn.sdh.backend.dto.UserInfoResponse;
import cn.sdh.backend.dto.UserStatsResponse;
import cn.sdh.backend.entity.Role;
import cn.sdh.backend.entity.User;
import cn.sdh.backend.mapper.RoleMapper;
import cn.sdh.backend.service.UserService;
import cn.sdh.backend.utils.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RoleMapper roleMapper;

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        String token = userService.login(request.getUsername(), request.getPassword());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);

        return Result.success("登录成功", data);
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request.getUsername(), request.getPassword(), request.getNickname());
        return Result.success("注册成功", null);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/info")
    public Result<UserInfoResponse> getUserInfo() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        User user = userService.getById(userId);
        if (user == null) {
            return Result.notFound("用户不存在");
        }

        UserInfoResponse response = new UserInfoResponse();
        BeanUtils.copyProperties(user, response);

        // 获取用户权限
        List<String> permissions = getUserPermissions(user.getRole());
        response.setPermissions(permissions);

        return Result.success(response);
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        // JWT是无状态的，登出只需前端删除Token即可
        // 如果需要服务端控制，可以将Token加入黑名单（Redis）
        return Result.success("登出成功", null);
    }

    /**
     * 更新个人信息
     */
    @PostMapping("/profile")
    public Result<Void> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        boolean success = userService.updateProfile(userId, request);
        return success ? Result.success("更新成功", null) : Result.error("更新失败");
    }

    /**
     * 修改密码
     */
    @PostMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        boolean success = userService.changePassword(userId, request);
        return success ? Result.success("密码修改成功", null) : Result.error("密码修改失败");
    }

    /**
     * 上传头像
     */
    @PostMapping("/avatar")
    public Result<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        if (file.isEmpty()) {
            return Result.error("请选择要上传的文件");
        }

        try {
            // 获取绝对路径
            Path basePath = Paths.get(uploadPath).toAbsolutePath().normalize();
            Path avatarDir = basePath.resolve("avatars");

            // 创建上传目录
            if (!Files.exists(avatarDir)) {
                Files.createDirectories(avatarDir);
            }

            // 生成文件名
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".jpg";
            String filename = userId + "_" + System.currentTimeMillis() + extension;

            // 保存文件
            Path filePath = avatarDir.resolve(filename);
            Files.copy(file.getInputStream(), filePath);

            // 更新用户头像URL
            String avatarUrl = "/uploads/avatars/" + filename;
            userService.updateAvatar(userId, avatarUrl);

            Map<String, String> data = new HashMap<>();
            data.put("avatar", avatarUrl);
            return Result.success("头像上传成功", data);
        } catch (IOException e) {
            log.error("上传头像失败", e);
            return Result.error("上传头像失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户统计数据
     */
    @GetMapping("/stats")
    public Result<UserStatsResponse> getUserStats() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        UserStatsResponse stats = userService.getUserStats(userId);
        return Result.success(stats);
    }

    /**
     * 根据角色编码获取权限列表
     */
    private List<String> getUserPermissions(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return Collections.emptyList();
        }

        // 查询角色
        Role role = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getCode, roleCode)
        );

        if (role == null || role.getStatus() != 1 || !StringUtils.hasText(role.getPermissions())) {
            return Collections.emptyList();
        }

        // 解析权限字符串
        return Arrays.asList(role.getPermissions().split(","));
    }
}
