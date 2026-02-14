package com.boyan.vir.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.boyan.vir.dto.ApiResult;
import com.boyan.vir.dto.LoginRequest;
import com.boyan.vir.dto.RegisterRequest;
import com.boyan.vir.entity.SysUser;
import com.boyan.vir.mapper.UserMapper;
import com.boyan.vir.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务 —— 注册 & 登录 & 登出
 *
 * Redis Key 规范:
 * virtual:auth:token:{userId} → JWT token string
 * TTL = JWT 过期时间 (默认 24h)
 */
@Service
public class UserService {

    /** Redis key 前缀 */
    private static final String TOKEN_KEY_PREFIX = "virtual:auth:token:";

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${app.jwt.expiration:86400000}")
    private long jwtExpiration;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 用户注册
     */
    public ApiResult<Map<String, Object>> register(RegisterRequest req) {
        // 1. 校验参数
        if (req.getUsername() == null || req.getUsername().isBlank()) {
            return ApiResult.error("用户名不能为空");
        }
        if (req.getPassword() == null || req.getPassword().length() < 4) {
            return ApiResult.error("密码不能少于4位");
        }

        // 2. 检查用户名是否已存在
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (count > 0) {
            return ApiResult.error("用户名已被注册");
        }

        // 3. 创建用户
        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname() != null && !req.getNickname().isBlank()
                ? req.getNickname()
                : req.getUsername());
        user.setStatus(1);

        userMapper.insert(user);

        // 4. 生成 JWT 并存入 Redis
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        saveTokenToRedis(user.getId(), token);

        // 5. 组装返回数据
        return ApiResult.success("注册成功", buildAuthData(token, user));
    }

    /**
     * 用户登录
     */
    public ApiResult<Map<String, Object>> login(LoginRequest req) {
        if (req.getUsername() == null || req.getUsername().isBlank()) {
            return ApiResult.error("用户名不能为空");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            return ApiResult.error("密码不能为空");
        }

        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (user == null) {
            return ApiResult.error("用户不存在");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return ApiResult.error("密码错误");
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            return ApiResult.error("账号已被禁用");
        }

        // 生成 JWT 并存入 Redis（覆盖旧 token，实现单设备登录）
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        saveTokenToRedis(user.getId(), token);

        return ApiResult.success("登录成功", buildAuthData(token, user));
    }

    /**
     * 用户登出 —— 从 Redis 删除 token
     */
    public void logout(Long userId) {
        String key = TOKEN_KEY_PREFIX + userId;
        redisTemplate.delete(key);
    }

    /**
     * 校验 token 是否在 Redis 中有效
     */
    public boolean isTokenValid(Long userId, String token) {
        String key = TOKEN_KEY_PREFIX + userId;
        Object stored = redisTemplate.opsForValue().get(key);
        return token.equals(stored);
    }

    /**
     * 将 token 存入 Redis，key = virtual:auth:token:{userId}
     */
    private void saveTokenToRedis(Long userId, String token) {
        String key = TOKEN_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, token, jwtExpiration, TimeUnit.MILLISECONDS);
    }

    /**
     * 构建前端期望的 { token, user: { id, username, nickname, avatar } }
     */
    private Map<String, Object> buildAuthData(String token, SysUser user) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("username", user.getUsername());
        userMap.put("nickname", user.getNickname());
        userMap.put("avatar", user.getAvatar());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", userMap);
        return data;
    }
}
