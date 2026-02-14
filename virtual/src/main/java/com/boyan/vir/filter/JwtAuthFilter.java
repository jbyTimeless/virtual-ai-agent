package com.boyan.vir.filter;

import com.boyan.vir.service.UserService;
import com.boyan.vir.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * JWT 认证过滤器
 * 对需要认证的接口校验 Bearer Token，并验证 Redis 中是否存在
 */
@Component
public class JwtAuthFilter implements Filter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();

        // 放行不需要认证的路径
        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 提取 Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(response, "未提供有效的认证令牌");
            return;
        }

        String token = authHeader.substring(7);

        // 1. 验证 JWT 签名和过期时间
        if (!jwtUtil.validateToken(token)) {
            sendUnauthorized(response, "认证令牌已过期或无效");
            return;
        }

        // 2. 验证 Redis 中是否存在该 token（防止已登出的 token 被重用）
        Long userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);

        if (!userService.isTokenValid(userId, token)) {
            sendUnauthorized(response, "认证令牌已失效，请重新登录");
            return;
        }

        // 将用户信息存入 request attribute，供后续使用
        request.setAttribute("userId", userId);
        request.setAttribute("username", username);

        chain.doFilter(request, response);
    }

    /**
     * 判断是否为公开路径（不需要认证）
     */
    private boolean isPublicPath(String path) {
        return path.startsWith("/api/auth/")
                || path.equals("/api/auth")
                || path.startsWith("/vir/")
                || path.equals("/vir")
                || path.equals("/error")
                || path.equals("/favicon.ico");
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = Map.of("code", 401, "message", message, "data", "");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
