# 单设备登录整改 PRD

> 当前状态：已完成
>
> 已完成标记日期：2026-07-03
>
> 本轮完成范围：
>
> - [x] 登录时递增 JWT token version。
> - [x] 密码、邮箱、短信登录统一使用登录专用签发逻辑。
> - [x] HTTP 拦截器继续按 Redis 当前 version 校验。
> - [x] WebSocket 握手补齐 version 校验，旧 token 无法新建连接。
> - [x] `/user/logout` 主动登出后递增 version。
> - [x] 前端主动退出登录调用后端登出接口，失败时仍本地清理。
> - [x] 补充登录签发与 WebSocket 握手单元测试。

## 1. 目标

- 同一账号只允许一个有效登录态。
- 新设备或新浏览器登录成功后，旧 token 下一次请求必须失效。
- HTTP 接口和 WebSocket 握手都必须遵守同一套 token 失效规则。
- 复用当前 `JWT tv 版本号` 机制，不新增复杂设备管理。

## 2. 非目标

- 不做设备列表。
- 不做可信设备。
- 不做多端在线管理。
- 不做浏览器指纹绑定。
- 不做“踢下线通知”。

## 3. 当前问题

- 当前 JWT 中已经携带 `tv` 版本号。
- 当前请求拦截器已经校验 JWT 中的 `tv` 是否等于 Redis 当前版本。
- 当前登录时只是读取当前 `tv` 后签发 token，没有递增 `tv`。
- 因此多次登录得到的是同版本 token，旧 token 仍然有效。
- 当前 WebSocket 握手只解析 JWT，没有校验 `tv`。

## 4. 整改方案

### 4.1 登录时递增 token 版本

整改要求：

- 在 `JwtTokenVersionService` 中新增 `nextVersion(userId)`。
- `nextVersion(userId)` 使用 Redis `INCR forum:jwt:tv:{userId}`。
- `nextVersion(userId)` 返回递增后的版本号。
- 登录签发 token 时必须使用 `nextVersion(userId)` 返回值。
- 密码登录、邮箱登录、短信登录都必须走同一签发逻辑。

验收标准：

- A 设备登录后，A token 可用。
- B 设备登录后，B token 可用。
- B 登录成功后，A token 请求受保护接口返回 401。

### 4.2 保留改密和找回密码强制失效

整改要求：

- 修改密码后继续递增 `tv`。
- 找回密码成功后继续递增 `tv`。
- 封禁、冻结、账号安全状态变化后继续递增 `tv`。

验收标准：

- 修改密码后，修改前签发的 token 立即失效。
- 找回密码后，找回前签发的 token 立即失效。

### 4.3 HTTP 拦截器继续校验版本

整改要求：

- `LoginInterceptor` 继续解析 JWT。
- `LoginInterceptor` 继续读取 JWT 中的 `tv`。
- `LoginInterceptor` 必须和 Redis 当前 `tv` 比对。
- 比对失败返回 401。

验收标准：

- 旧 token 不进入 Controller。
- Controller 中拿到的 `USER_SESSION` 一定来自当前有效 token。

### 4.4 WebSocket 握手补齐版本校验

整改要求：

- `TokenHandshakeInterceptor` 解析 JWT 后必须读取 `tv`。
- `TokenHandshakeInterceptor` 必须校验 Redis 当前 `tv`。
- 版本不一致时拒绝握手，返回 401。

验收标准：

- B 设备登录后，A 设备旧 token 无法新建 WebSocket 连接。
- 非法 token、过期 token、旧版本 token 都无法完成握手。

### 4.5 登出接口

整改要求：

- 新增或补齐 `/user/logout`。
- 当前用户主动退出时递增 `tv`。
- 退出成功后，当前 token 立即失效。
- 登出失败不得暴露 token 内容。

验收标准：

- 用户退出后，原 token 请求受保护接口返回 401。
- 重复退出返回明确结果，不产生异常堆栈。

## 5. 涉及文件

- `backend/src/main/java/org/example/forumdemo/service/impl/user/JwtTokenVersionService.java`
- `backend/src/main/java/org/example/forumdemo/service/impl/user/AuthTokenService.java`
- `backend/src/main/java/org/example/forumdemo/service/impl/user/UserServiceImpl.java`
- `backend/src/main/java/org/example/forumdemo/service/impl/user/MailCodeServiceImpl.java`
- `backend/src/main/java/org/example/forumdemo/service/impl/user/SMSCodeServiceImpl.java`
- `backend/src/main/java/org/example/forumdemo/common/interceptor/LoginInterceptor.java`
- `backend/src/main/java/org/example/forumdemo/common/interceptor/TokenHandshakeInterceptor.java`
- `backend/src/main/java/org/example/forumdemo/controller/UserController.java`

## 6. 测试用例

### 6.1 密码登录单设备

1. A 登录，保存 tokenA。
2. tokenA 请求 `/user/session` 成功。
3. B 登录，保存 tokenB。
4. tokenB 请求 `/user/session` 成功。
5. tokenA 再请求 `/user/session` 返回 401。

### 6.2 邮箱登录单设备

1. A 使用密码登录。
2. B 使用邮箱验证码登录同一账号。
3. A 的旧 token 返回 401。
4. B 的新 token 正常使用。

### 6.3 短信登录单设备

1. A 使用密码登录。
2. B 使用短信验证码登录同一账号。
3. A 的旧 token 返回 401。
4. B 的新 token 正常使用。

### 6.4 WebSocket 旧 token

1. A 登录并建立 WebSocket。
2. B 登录同一账号。
3. A 使用旧 token 新建 WebSocket 连接失败。
4. B 使用新 token 新建 WebSocket 连接成功。

### 6.5 登出失效

1. 用户登录后保存 token。
2. 调用 `/user/logout`。
3. 原 token 请求受保护接口返回 401。

## 7. 实施顺序

1. `JwtTokenVersionService` 增加 `nextVersion(userId)`。
2. `AuthTokenService` 增加登录专用签发方法，内部使用 `nextVersion(userId)`。
3. 密码、邮箱、短信登录统一改用登录专用签发方法。
4. WebSocket 握手补齐 `tv` 校验。
5. 补齐 `/user/logout`。
6. 增加单设备登录测试。
