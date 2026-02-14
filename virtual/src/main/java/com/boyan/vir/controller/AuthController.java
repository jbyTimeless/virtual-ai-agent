package com.boyan.vir.controller;

import com.boyan.vir.dto.ApiResult;
import com.boyan.vir.dto.LoginRequest;
import com.boyan.vir.dto.RegisterRequest;
import com.boyan.vir.service.UserService;
import com.boyan.vir.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器 —— 登录 / 注册 / 登出
 * 路径前缀: /api/auth
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ApiResult<Map<String, Object>> login(@RequestBody LoginRequest req) {
        return userService.login(req);
    }

    /**
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ApiResult<Map<String, Object>> register(@RequestBody RegisterRequest req) {
        return userService.register(req);
    }

    /**
     * POST /api/auth/logout
     * 从 Redis 中删除 token，使其立即失效
     */
    @PostMapping("/logout")
    public ApiResult<Void> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.getUserIdFromToken(token);
                userService.logout(userId);
            }
        }
        return ApiResult.success("已退出登录", null);
    }
}
