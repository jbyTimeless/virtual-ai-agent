# VirtualAi 接口文档

> Base URL: `http://localhost:8080`
> API 统一前缀: `/api`
> 认证方式: JWT Bearer Token（在 `Authorization` 请求头中携带 `Bearer <token>`）

---

## 1. 认证模块 `/api/auth`

### 1.1 用户注册

| 项目             | 说明                       |
|-----------------|---------------------------|
| **URL**         | `POST /api/auth/register`  |
| **认证**        | 不需要                      |
| **Content-Type**| `application/json`         |

**请求参数：**

| 字段       | 类型     | 必填 | 说明             |
|-----------|---------|------|-----------------|
| username  | String  | ✅   | 用户名（唯一）     |
| password  | String  | ✅   | 密码（≥4位）       |
| nickname  | String  | ❌   | 昵称（默认=用户名） |

**请求示例：**
```json
{
  "username": "testuser",
  "password": "123456",
  "nickname": "测试用户"
}
```

**成功响应 `200`：**
```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "user": {
      "id": 1,
      "username": "testuser",
      "nickname": "测试用户",
      "avatar": null
    }
  }
}
```

**失败响应：**
```json
{ "code": 400, "message": "用户名已被注册", "data": null }
{ "code": 400, "message": "用户名不能为空", "data": null }
{ "code": 400, "message": "密码不能少于4位", "data": null }
```

---

### 1.2 用户登录

| 项目             | 说明                     |
|-----------------|-------------------------|
| **URL**         | `POST /api/auth/login`   |
| **认证**        | 不需要                    |
| **Content-Type**| `application/json`       |

**请求参数：**

| 字段       | 类型     | 必填 | 说明    |
|-----------|---------|------|--------|
| username  | String  | ✅   | 用户名  |
| password  | String  | ✅   | 密码    |

**请求示例：**
```json
{
  "username": "testuser",
  "password": "123456"
}
```

**成功响应 `200`：**
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "user": {
      "id": 1,
      "username": "testuser",
      "nickname": "测试用户",
      "avatar": null
    }
  }
}
```

**失败响应：**
```json
{ "code": 400, "message": "密码错误", "data": null }
{ "code": 400, "message": "用户不存在", "data": null }
{ "code": 400, "message": "账号已被禁用", "data": null }
```

---

### 1.3 用户登出

| 项目             | 说明                              |
|-----------------|----------------------------------|
| **URL**         | `POST /api/auth/logout`           |
| **认证**        | 需要（携带 Bearer Token）           |
| **Content-Type**| `application/json`                |

> 登出时服务端会从 Redis 中删除该用户的 token，使其立即失效。

**成功响应 `200`：**
```json
{
  "code": 200,
  "message": "已退出登录",
  "data": null
}
```

---

## 2. AI 对话模块 `/vir`

### 2.1 AI 对话（流式）

| 项目             | 说明                     |
|-----------------|-------------------------|
| **URL**         | `GET /vir/hello`         |
| **认证**        | 当前不需要（后续可加）      |

**请求参数：**

| 字段  | 类型    | 必填 | 说明         |
|------|--------|------|-------------|
| msg  | String | ✅   | 用户消息内容   |

**请求示例：**
```
GET /vir/hello?msg=你好
```

**响应：** SSE 流式返回（`text/event-stream`），逐字返回 AI 回复内容。

---

## 3. 通用说明

### 3.1 统一响应格式

所有接口返回统一 JSON 结构：

```json
{
  "code": 200,
  "message": "描述信息",
  "data": { ... }
}
```

| code | 含义               |
|------|--------------------|
| 200  | 成功               |
| 400  | 请求参数错误/业务失败 |
| 401  | 未认证/token 过期    |

### 3.2 认证机制

- 登录/注册成功后，返回 JWT `token`，同时存入 Redis
- 后续需要认证的请求，在 Header 中携带：
  ```
  Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
  ```
- Token 有效期 **24 小时**（Redis TTL 同步）
- Token 过期或登出后返回 `401`，前端自动跳转登录页
- **单设备登录**：重新登录会覆盖旧 token，旧设备自动失效

### 3.3 Redis Key 命名规范

| Key 模式                         | 值      | TTL    | 说明                |
|---------------------------------|---------|--------|--------------------|
| `virtual:auth:token:{userId}`   | JWT 字符串 | 24 小时 | 用户登录态 token 存储 |

### 3.4 公开接口（无需 Token）

| 路径                      | 说明     |
|--------------------------|---------|
| `POST /api/auth/login`    | 登录    |
| `POST /api/auth/register` | 注册    |
| `POST /api/auth/logout`   | 登出    |
| `GET /vir/**`             | AI 对话  |

### 3.4 前端代理配置

前端 Vite dev server 将 `/api` 前缀的请求统一代理到后端：

```typescript
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true
  }
}
```

### 3.5 用户表结构 `sys_user`

| 字段         | 类型          | 说明              |
|-------------|--------------|------------------|
| id          | BIGINT (PK)  | 自增主键           |
| username    | VARCHAR(50)  | 用户名（唯一）      |
| password    | VARCHAR(255) | BCrypt 加密密码    |
| nickname    | VARCHAR(50)  | 昵称              |
| avatar      | VARCHAR(255) | 头像 URL          |
| status      | TINYINT      | 1=正常, 0=禁用     |
| created_at  | DATETIME     | 创建时间           |
| updated_at  | DATETIME     | 更新时间（自动更新） |
