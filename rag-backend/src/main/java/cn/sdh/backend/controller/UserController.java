package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.LoginRequest;
import cn.sdh.backend.dto.RegisterRequest;
import cn.sdh.backend.dto.UserInfoResponse;
import cn.sdh.backend.entity.User;
import cn.sdh.backend.service.UserService;
import cn.sdh.backend.utils.JwtUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

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
}
