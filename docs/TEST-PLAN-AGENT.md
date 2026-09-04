# mengmeng_forum 自动化测试执行手册（面向 AI Agent）

> 配套文档：[TEST-PLAN.md](TEST-PLAN.md)（面向人工测试的全量清单，535 条）。
> **本文只收录能被程序判定通过 / 失败的用例**，并补上人工文档里没有的东西：环境契约、认证引导、数据种子、精确断言。
> 版本：2026-09-04（第 2 版）· 基线提交 `5a29c05b`
>
> **第 2 版修订**（依据 2026-09-04 首轮执行结果实测校正）：网关端口 8080→**10086**；未登录码 1001→**1106**；
> `GW-A08` 期望 403→**404（本就正确，非缺陷）**；`GW-A09` 由 P0 降为 **P2 加固**并附三层实测证据；
> `ART-A02/A03` 期望 1002→**0**（下限在 `submitForAudit` 把关，另补 A03b/A03c）；
> **`G5` 全组加上强制查库断言**——首轮 `VIP-A40`/`A53` 因只断言响应文本而假通过。

---

## A. 执行前必读的硬规则

**违反以下任意一条，请立即停止并向人类报告，不要自行变通。**

### A1. 绝不做的事

| 禁止 | 原因 |
| --- | --- |
| 在**有数据的库**上执行 `init-db` / `reset-db` / `V*.sql` 迁移 | 不可逆。迁移 `V20260903__vip_order_fields.sql` 已执行过，`ADD COLUMN` 不幂等 |
| 试图识别、绕过或自动求解滑块验证码 | 正确做法见 §C：在测试环境**注入票据**，走系统自己的签发状态，而不是攻破校验 |
| 执行 §H 标注为 `FORBIDDEN` 的任何用例 | 会产生真实费用或不可逆写入 |
| 对生产环境执行本文任何一条 | 本手册只针对本地 / 隔离测试环境 |
| 在联系不上人类时"猜一个契约试试" | 见 A3 |

### A2. 会花真钱的调用

以下路径每次调用都**真实计费**（通义千问 / Wan 生图 / Tavily 搜索），参考单价见 `docs/api-model-pricing.md`：

```
/ai/polish            /ai/article-cover      /ai/cover-hints
/article/tag/suggest  /article/summary/regenerate
/article/creator/insight                     /article/music/ai-search
/search/article（AI 检索分支）
/mascot/chat          /mascot/chat/stream
/user/profile/change-request（昵称简介审核）
提交审核 /article/submitForAudit → 触发 Python 审核图
```

**规则**：这些路径每条用例**最多调用一次**，失败**不重试**，不得放进循环或压测。预算耗尽或返回 `1164` 时停止该组，报告后等待人类指示。

### A3. 契约不确定时的行为

本文标注 `契约已核实` 的用例，请求体与断言可直接使用。
标注 `契约待确认` 的，**先读对应 Controller 与 DTO 源码确认字段名，再构造请求**。

**绝不允许**猜字段名后把 `1002 参数校验失败` 当成产品缺陷上报——这是本类任务最高频的假阳性来源。无法确认契约时，把该用例标记为 `BLOCKED` 并说明缺什么。

### A4. 判定纪律

- 断言只针对本文写明的字段。**不要**因为返回体多了或少了未声明的字段就判失败。
- 一条用例失败时，先重跑一次确认稳定复现（§A2 列出的付费路径除外）。
- 区分三种结果：`PASS` / `FAIL`（产品行为与期望不符）/ `BLOCKED`（环境、契约或前置数据不具备）。**不要**把 BLOCKED 报成 FAIL。

---

## B. 环境契约

### B1. 服务与端口

| 组件 | 默认地址 | 说明 |
| --- | --- | --- |
| **Gateway（唯一入口）** | `http://127.0.0.1:10086` | 所有 REST 用例都打这里，除非用例特别注明 |
| 六域直连（仅排障用） | auth `10101` · content `10102` · im `10103` · game `10104` · economy `10105` · ai `10106` | **用例不要直连**，绕过网关会漏掉 `/internal/**` 拦截等断言 |
| 前端 dev | `http://127.0.0.1:5173` | 仅 UI 类用例需要 |
| MySQL | `127.0.0.1:33306` | 六库：`forum_auth_db` / `forum_content_db` / `forum_im_db` / `forum_game_db` / `forum_economy_db` / `forum_ai_db` |
| Redis | `127.0.0.1:16530` | 认证引导与部分断言要读写 |
| RabbitMQ | `127.0.0.1:56720`（管理口 15672 映射见 compose） | 异步链路断言 |
| PostgreSQL | `127.0.0.1:54320` | LangGraph 运行态 |
| Nacos | 控制台 `http://127.0.0.1:8080/index.html` · 服务端 `127.0.0.1:8848` | 注意 8080 是 Nacos 控制台，**不是** Gateway |
| ai-server | `http://127.0.0.1:5000`（以 `ai-server/config.yaml` 为准） | 只有 `PY-*` 组直连 |

> 端口可被环境变量覆盖（`MYSQL_HOST_PORT` 等）。**执行前先从 `deploy/` 的 compose 与实际进程确认真实端口**，不要硬编码本表。

### B2. 前置检查（不通过就不要开始）

按顺序执行，任一失败即 `BLOCKED` 并报告：

1. 六个 Java 服务 + gateway 进程存活，且在 Nacos 中注册
2. `docker compose ps` 六个中间件全部 healthy —— **进程"起来了"不等于 healthy**
3. `curl http://127.0.0.1:10086/board/topBoardList` 返回 `code=0` 且响应头带 `X-Trace-Id`（证明 Gateway → content → MySQL 整条通）
4. Redis 可连且可写（认证引导依赖）
5. ai-server 健康检查通过（仅当要跑 `AI-*` / `MAS-*` / `PY-*`）

> 探测失败时**必须区分**「连不上 / 没权限 / 容器没起」与「查到 0 行」。禁止用 `2>/dev/null`、`|| true`、`except: pass` 把前者伪装成后者。

### B3. 统一响应信封

除两个例外，所有 REST 响应都是：

```json
{ "code": 0, "message": "成功", "data": <任意> }
```

- `code=0` 表示成功，非 0 为业务码（见 §B4）
- **HTTP 状态码通常仍是 200**，判定一律看 body 的 `code`，不要看 HTTP 状态
- 响应头带 `X-Trace-Id`，失败时请连同它一起上报

**两个例外**：

| 例外 | 形状 |
| --- | --- |
| `POST /vip/payment/callback/{channel}` | 纯文本 `success` / `fail`，**不是** JSON 信封 |
| `POST /mascot/chat/stream` | SSE，`text/event-stream`，每行 `data: {...}` |

### B4. 本文用到的业务码

| 码 | 含义 |
| --- | --- |
| `0` | 成功 |
| `1001` | `FAILED_UNAUTHORIZED`，枚举里存在，但**拦截器不用它**——未登录一律是 1106 |
| `1106` | `USER_UNLOGIN` 登录状态已过期。**这是未登录的实际返回码**，HTTP 状态 401 |
| `2000` | 服务端异常兜底 |
| `1002` | 参数校验失败 |
| `1003` | 无权限 / 禁止访问 |
| `1005` | 内容不存在或已删除 |
| `1101` | 账号已被注册 |
| `1102` | 用户不存在 |
| `1103` | 账号或密码不正确 |
| `1164` | AI 额度已用完 |
| `1210` | 登录失败次数过多 |
| `1224` | 不支持的支付方式 |
| `1225` | 订单不存在或已关闭 |
| `1226` | 已是更高档位会员，不可购买低档位 |
| `1227` | 会员状态已变化，请重新下单 |
| `1228` | 订单金额校验不通过 |

完整表在 `common/src/main/java/org/pluchon/forum/common/enums/ResultCode.java`。

### B5. 认证头

```
Authorization: <token>
```

字段名来自 `AuthConstants.JWT_NAME`，值就是登录返回的 `data.token`。**注意没有 `Bearer ` 前缀**，直接放原始 token。

---

## C. 认证引导：怎么拿到 token

### C1. 为什么需要引导

`UserAuthFlowServiceImpl` 中密码登录、邮箱登录、短信登录、发码、找回密码**每个方法第一行都是 `requireCaptchaTicket(...)`**，票据缺失或用途不符一律拒绝。注册同样强制。因此没有引导步骤，agent 一条用例都跑不了。

### C2. 票据是什么

`CaptchaTicketServiceImpl` 的实现很简单：

- Redis key：`forum:captchaTicket:<ticket>`
- value：用途字符串
- TTL：`captcha.expire.default`，默认 **120000 ms（2 分钟）**
- 消费：原子比对用途后删除，**一次性**

用途常量（`CaptchaTicketPurpose`）：
`USER_LOGIN` · `REGISTER` · `MAIL_SEND` · `MAIL_LOGIN` · `SMS_SEND` · `SMS_LOGIN` · `RESET_SEND` · `RESET_SUBMIT`

### C3. 测试环境的正当引导方式

**在测试环境里，直接写入系统自己会写的那条 Redis 记录**——这是复现「滑块已通过」这一状态，不是攻破校验逻辑：

```bash
# 生成一个票据并种入 Redis（示例用途为密码登录）
TICKET=$(python -c "import uuid;print(uuid.uuid4().hex)")
redis-cli -h 127.0.0.1 -p 16530 SET "forum:captchaTicket:$TICKET" "USER_LOGIN" EX 120
```

然后登录：

```
POST /user/login
Header: X-Captcha-Ticket: <TICKET>
Body:   { "userName": "<账号>", "password": "<密码>" }
→ data.token / data.user
```

**要点**

- 票据一次性，每次调用认证接口都要重新种一张
- 用途必须与接口匹配，`USER_LOGIN` 的票不能用于注册
- 2 分钟内用掉
- 这条口子**只允许在本地 / 隔离测试环境使用**；不得写进生产脚本，也不得在生产 Redis 上执行

> 如果你的环境不允许直连 Redis，请求人类为测试环境加一个 dev-only 的票据签发端点（受配置开关控制、默认关闭）。**不要**尝试自动求解滑块。

### C4. 登录不上的排查顺序

1. 票据是不是已被消费（Redis 里 key 还在吗）
2. 用途是否匹配
3. `1210` = 之前失败次数过多触发锁定，换账号或等待
4. `1103` = 账号密码本身不对，不是引导问题

---

## D. 数据种子

### D1. 原则

会员用例依赖精确的前置状态（剩余天数、首购资格、当初的定价体系），**这些状态没有 API 可以造**，只能直接写库。

- 只在**测试库**执行
- 每组用例执行前重置一次前置，不要依赖上一条用例的残留
- 写库后**必须清用户缓存**，否则档位读的是 Redis 旧值：
  `redis-cli DEL "forum:user:info:<userId>"`（键名以 `ForumRedisKeys` 为准，不确定就重启 economy + ai 或等 TTL 过期）

### D2. 相关表

`forum_economy_db.user_vip_subscription`（每人一行，`user_id` 唯一）

| 列 | 用途 |
| --- | --- |
| `vip_tier` | 0 免费 / 1 PRO / 2 MAX |
| `vip_expire_at` | 会员到期日，`NULL` = 长期有效 |
| `base_quota_tier` | 基础配额档位 |
| `quota_period_start` / `quota_period_end` | 配额周期窗口 |

`forum_economy_db.vip_purchase_record`（订单与首购资格流水）

| 列 | 用途 |
| --- | --- |
| `payment_state` | 0 待支付 / 1 成功 / 2 关闭 |
| `price_plan` | `first_purchase` / `normal` |
| `order_kind` | `new` / `renew` / `upgrade` |
| `expected_expire_at` | 下单时锁定的到期日，发货前比对 |
| `paid_amount` | 实付金额，回调金额要与它一致 |

> **首购资格 = 该用户没有任何 `payment_state=1` 且 `delete_state=0` 的流水。**

### D3. 种子脚本

```sql
-- 变量：把 :uid 换成真实 userId

-- U-NEW：全新号，首购资格在
DELETE FROM vip_purchase_record WHERE user_id = :uid;
INSERT INTO user_vip_subscription (user_id, vip_tier, vip_expire_at, base_quota_tier)
VALUES (:uid, 0, NULL, 0)
ON DUPLICATE KEY UPDATE vip_tier=0, vip_expire_at=NULL, base_quota_tier=0,
                        quota_period_start=NULL, quota_period_end=NULL;

-- U-FREE：免费档但首购资格已用掉（造一条历史成功流水）
INSERT INTO vip_purchase_record
  (user_id, vip_tier, paid_amount, payment_order_no, payment_channel,
   price_plan, order_kind, payment_state, paid_at)
VALUES (:uid, 1, 3.90, CONCAT('SEED', :uid, UNIX_TIMESTAMP()), 'mock',
        'first_purchase', 'new', 1, NOW());

-- U-PRO-30D：PRO 剩 30 天，当初按首购价买的
UPDATE user_vip_subscription
   SET vip_tier=1, vip_expire_at=DATE_ADD(NOW(), INTERVAL 30 DAY), base_quota_tier=1
 WHERE user_id = :uid;
-- 同时确保存在一条 price_plan='first_purchase' 的 PRO 成功流水（升级差价取它）

-- U-PRO-1D：PRO 剩 1 天（白嫖用例前置）
UPDATE user_vip_subscription
   SET vip_tier=1, vip_expire_at=DATE_ADD(NOW(), INTERVAL 1 DAY), base_quota_tier=1
 WHERE user_id = :uid;

-- U-MAX：MAX 有效
UPDATE user_vip_subscription
   SET vip_tier=2, vip_expire_at=DATE_ADD(NOW(), INTERVAL 30 DAY), base_quota_tier=2
 WHERE user_id = :uid;

-- U-TRIAL：体验卡来的 PRO —— 有会员但没有任何 PRO 购买流水
DELETE FROM vip_purchase_record WHERE user_id = :uid;
UPDATE user_vip_subscription
   SET vip_tier=1, vip_expire_at=DATE_ADD(NOW(), INTERVAL 30 DAY), base_quota_tier=1
 WHERE user_id = :uid;

-- U-PRO-LIFETIME：到期日为空（长期有效），验升级不被当成剩 0 天
UPDATE user_vip_subscription
   SET vip_tier=1, vip_expire_at=NULL, base_quota_tier=1 WHERE user_id = :uid;
```

### D4. 清理

每组用例结束后清掉本组产生的订单，避免污染下一组的首购资格判定：

```sql
DELETE FROM vip_purchase_record
 WHERE user_id = :uid AND payment_order_no LIKE 'V%';
```

---

## E. 通用断言约定

| 记法 | 含义 |
| --- | --- |
| `code=N` | 响应 body 的 `code` 字段等于 N |
| `data.x = v` | 响应 data 中该字段精确等于 v |
| `DB: 表.列 = v` | 直接查库断言（写操作类用例必须查库，不能只信响应） |
| `不变` | 与操作前的快照相同（操作前先取一次基线） |
| `AUTH` | 需要 `Authorization` 头 |
| `ANON` | 不带任何认证头 |

**金额比较用十进制精确比较**（`BigDecimal` / `Decimal`），不要用浮点。`2.00` 与 `2.0` 视为相等，`2.001` 视为不等。

---

## F. 可自动化用例

### F1. 网关与鉴权（GW-A）· 契约已核实

| 编号 | 请求 | 期望 |
| --- | --- | --- |
| GW-A01 | `ANON GET /board/topBoardList` | `code=0`（白名单可匿名） |
| GW-A02 | `ANON GET /points/wallet` | `code=1106`（HTTP 401） |
| GW-A03 | `AUTH(伪造token) GET /points/wallet` | `code=1106`（HTTP 401），且响应不含堆栈或解析细节 |
| GW-A04 | `ANON GET /user/internal/1/exists` | HTTP 403 且 body `code=1003` |
| GW-A05 | `ANON POST /vip/internal/1/tier` | HTTP 403，`code=1003` |
| GW-A06 | `ANON GET /points/internal/1/balance` | HTTP 403，`code=1003` |
| GW-A07 | `ANON GET /shop/internal/...` | HTTP 403，`code=1003` |
| GW-A08 | `ANON GET //internal/x`（无路由前缀的双斜杠） | **HTTP 404**。这是正确行为，不是缺陷：Gateway 在路由未匹配时由 `RoutePredicateHandlerMapping` 先抛 NotFound，全局过滤器根本不执行；没有路由就没有下游可达 |
| GW-A08b | `ANON GET /user//internal/1/exists`（已匹配路由内的双斜杠） | HTTP 403，`code=1003` |
| GW-A09 | `ANON GET /user/INTERNAL/1/exists`（大小写变形） | **当前实际：HTTP 401 / `1106`**（穿透了网关过滤器，被下游 auth 拦截器挡下）。见下方说明 |
| GW-A10 | `ANON GET /user/%2Finternal%2Fx`（URL 编码变形） | 403 |
| GW-A11 | 任意请求 | 响应头存在 `X-Trace-Id` 且非空 |
| GW-A12 | `ANON GET /404路径` | 不返回 5xx，不泄露内部异常 |
| GW-A13 | `ANON GET /article/createDraft`（对 POST-only 端点发 GET） | 期望 405；**当前实际 HTTP 500 / `code=2000`**，方法不匹配被兜底成服务端异常 |

> **GW-A09 的定级（已核实，不要再报成 P0）**：过滤器 `path.contains("/internal/")` 确实大小写敏感、确实能被绕过，但绕过后还有三道：
> ① auth 拦截器的 exclude 是小写 `/user/internal/**`，同样大小写敏感，变形请求反而要求登录态；
> ② 内部接口自带**内部密钥校验**（直连 `10101` 打小写内部路径、不带凭据，返回 `403 / 1003`）；
> ③ Spring MVC handler mapping 大小写敏感。
> 因此**不可利用**，属于纵深防御被削掉一层，定级 **P2 加固**。
> 修复：`path.toLowerCase(Locale.ROOT).contains("/internal/")` —— 必须带 `Locale.ROOT`，否则土耳其 locale 下 `"INTERNAL"` 会被转成 `"ınternal"` 而漏掉。

### F2. 账号边界（AUTH-A）· 契约已核实

前置：每条都先按 §C3 种一张对应用途的票据。

| 编号 | 请求 | 期望 |
| --- | --- | --- |
| AUTH-A01 | `POST /user/login` 不带票据 | 非 0（拒绝），不返回 token |
| AUTH-A02 | `POST /user/login` 带 `REGISTER` 用途的票据 | 拒绝（用途不匹配） |
| AUTH-A03 | 同一票据连用两次登录 | 第二次拒绝（一次性） |
| AUTH-A04 | 票据超过 120s 后使用 | 拒绝 |
| AUTH-A05 | 正确票据 + 正确账号密码 | `code=0`，`data.token` 非空 |
| AUTH-A06 | 正确票据 + 错误密码 | `code=1103` |
| AUTH-A07 | 连续 5 次错误密码后再试 | `code=1210` |
| AUTH-A08 | `POST /user/register` 用已存在的用户名 | `code=1101` |
| AUTH-A09 | `AUTH PUT /user/modifyUser` body 含 `email` 字段 | **邮箱不得被修改**：`DB: user.email 不变`（这是账号接管链的防线，改成功即 P0 缺陷） |
| AUTH-A10 | `AUTH PUT /user/modifyUser` body 含 `phoneNum` 字段 | 同上，`DB: user.phone_num 不变` |
| AUTH-A11 | `AUTH GET /user/loginLogs?page=1&size=10` | `code=0`，返回条数 ≤ size |
| AUTH-A12 | `AUTH PUT /user/followUser` 关注自己 | 非 0 |
| AUTH-A13 | 登录成功后的响应体 | 不含 `password`、`salt` 字段 |

### F3. 内容边界（ART-A）· 契约已核实

`POST /article/createDraft` 请求体：

```json
{ "boardId": 1, "title": "...", "content": "...", "contentType": 0, "articleType": 0, "tagIds": [] }
```

| 编号 | 请求 | 期望 |
| --- | --- | --- |
| ART-A01 | title 3 字 + content 6 字 | `code=0`，返回 `data` 为帖子 ID |
| ART-A02 | title 2 字 | **`code=0`**——草稿接口**只挡上限不挡下限**（`PublishArticleRequest` 注释写明：草稿允许写一半先存着，下限属于发布门槛）。返回 1002 反而是缺陷 |
| ART-A03 | content 5 字 | **`code=0`**，同上 |
| ART-A03b | 用 ART-A02 建出的草稿调 `POST /article/submitForAudit` | **非 0**——「标题≥3」的发布门槛在这里把关 ⚠️ 触发真实 AI 调用，见 §A2，只跑一次 |
| ART-A03c | 用 ART-A03 建出的草稿调 `submitForAudit` | **非 0**——「正文≥6」门槛 ⚠️ 同上 |
| ART-A04 | title 100 字（上界） | `code=0` |
| ART-A05 | title 101 字 | `code=1002` |
| ART-A06 | content 20000 字（上界） | `code=0` |
| ART-A07 | content 20001 字 | `code=1002` |
| ART-A08 | `ANON POST /article/createDraft` | `code=1106`（HTTP 401） |
| ART-A09 | `AUTH(U-B) DELETE /article/deleteArticle` 删 U-A 的帖子 | 非 0，`DB: article.delete_state 不变` |
| ART-A10 | `ANON GET /article/selectArticleDetailByArticleId?articleId=<草稿ID>` | 非 0（未发布不进详情） |
| ART-A11 | `AUTH(U-B) GET` 同上（非作者读他人草稿） | 非 0 |
| ART-A12 | `AUTH POST /article/updateCoverUrl` 传外链 `https://evil.example/a.png` | 非 0，`DB: article.cover_url 不变` |
| ART-A13 | 同上，传一个 `_pending/` 路径的 URL 但未经绑定流程 | 按正式目录校验应拒绝；若通过即为 P0 缺陷 |
| ART-A14 | `AUTH GET /article/getHotArticleListWithPage?page=1&size=20` | `code=0`，返回条数 ≤ 14（单页上限） |

### F4. 互动与弹幕（CMT-A）· 契约已核实

`PUT /articleDanmaku/send` 请求体：

```json
{ "articleId": 1, "content": "...", "colorCode": 0, "videoTimeMs": 1000, "mode": 0, "fontSize": 0 }
```

| 编号 | 请求 | 期望 |
| --- | --- | --- |
| CMT-A01 | content 30 字 | `code=0` |
| CMT-A02 | content 31 字 | 非 0 |
| CMT-A03 | 1 分钟内连发 21 条 | 第 21 条被限频拒绝 |
| CMT-A04 | 连发两条完全相同的内容 | 第二条被去重拒绝 |
| CMT-A05 | `GET /articleDanmaku/listByTimeWindow?articleId=1&fromMs=0&toMs=120000` | `code=0` |
| CMT-A06 | 同上 `toMs=120001`（窗口 > 120s） | 非 0 |
| CMT-A07 | `ANON PUT /articleDanmaku/send` | `code=1106`（HTTP 401） |
| CMT-A08 | 同一用户重复点赞同一帖子两次 | 幂等：`DB: 点赞数` 只 +1 |
| CMT-A09 | `AUTH(U-B) DELETE /articleReply/deleteOwnReply` 删 U-A 的评论 | 非 0，评论仍存在 |
| CMT-A10 | U-A 自删有子回复的评论 | `code=0`，且 `DB: 子回复 delete_state 仍为 0`（自删保留子回复） |

### F5. 收藏（FAV-A）· 契约待确认（字段名先读 `FavoriteController`）

| 编号 | 请求 | 期望 |
| --- | --- | --- |
| FAV-A01 | 连续创建收藏夹至 20 个 | 第 20 个 `code=0` |
| FAV-A02 | 创建第 21 个 | 非 0 |
| FAV-A03 | `AUTH(U-B) DELETE /favorite/folder/{U-A的folderId}` | 非 0，收藏夹仍在 |
| FAV-A04 | `AUTH(U-B) PUT /favorite/article/move` 移动到 U-A 的收藏夹 | 非 0 |
| FAV-A05 | 重复收藏同一帖子 | 幂等，收藏数只 +1 |
| FAV-A06 | `ANON GET /favorite/folder/myList` | `code=1106`（HTTP 401） |

### F6. 举报（MOD-A）· 契约已核实

`POST /article/report` 请求体：`{ "targetType": "...", "targetId": 1, "reason": "..." }`
（`targetType` 取值先从 `ContentReportServiceImpl` 的常量确认：帖子 1 / 评论 2 / 楼中楼 3 / 弹幕 4 / 歌曲 5）

| 编号 | 请求 | 期望 |
| --- | --- | --- |
| MOD-A01 | 举报一首歌，第 1 个用户 | `code=0`，`DB: content_ai_task.task_id LIKE 'waiting-%'`（占位号） |
| MOD-A02 | 第 2 个不同用户举报同一首 | 仍是占位号，未触发 AI |
| MOD-A03 | 同一用户重复举报同一首 | 不增加计数，`DB` 中该目标的不同举报人数仍为 2 |
| MOD-A04 | 第 3 个不同用户举报同一首 | 占位号被改写成真 taskId（不再以 `waiting-` 开头），三条举报单**都**指向同一 taskId ⚠️ 本条会触发一次真实 AI 调用，见 §A2 |
| MOD-A05 | 单用户当日提交第 21 条举报 | 非 0（每日上限 20） |
| MOD-A06 | 举报一个已删除的帖子 | 直接按已处理返回，不新建 AI 任务 |
| MOD-A07 | `ANON POST /article/report` | `code=1106`（HTTP 401） |

### F7. 经济幂等（ECO-A）· 契约待确认

| 编号 | 请求 | 期望 |
| --- | --- | --- |
| ECO-A01 | 当日重复签到 | 第二次非 0，`DB: 积分不再增加` |
| ECO-A02 | 无补签卡时补签 | 非 0 |
| ECO-A03 | 补签请求里自带一个日期参数（尝试自选日期） | 服务端忽略该参数，只补最近的漏签日 —— 若能按客户端指定日期补签即为 P0 缺陷 |
| ECO-A04 | 星辉余额不足时兑换 | 非 0，`DB: 星辉余额不变` |
| ECO-A05 | 并发发起 10 次同一兑换 | 只成功 1 次，`DB: 物品只入账 1 件` |
| ECO-A06 | 并发点击 10 次「使用背包物品」 | 只发放 1 次 |
| ECO-A07 | 重复领取同一里程碑 | 第二次非 0 |
| ECO-A08 | `GET /points/center/overview` 连续调用 10 次 | 积分余额不变（GET 无写副作用） |
| ECO-A09 | `GET /lottery/info` 连续调用 10 次 | 不产生里程碑发放，余额不变 |
| ECO-A10 | 表情商城定价 5001 | 非 0（上限 5000） |
| ECO-A11 | 表情商城定价 0 | `code=0`（下限 0 合法） |
| ECO-A12 | `ANON GET /bag/items` | `code=1106`（HTTP 401） |

---

## G. 会员与订单（VIP-A）★ 全部契约已核实

> 这是本手册价值最高、也最适合自动化的一组：期望值全是精确数字与精确业务码。

### G1. 契约

```
POST /vip/order/create      AUTH  { "tier": 1|2, "payChannel": "mock" }
GET  /vip/order/query       AUTH  ?orderNo=<订单号>
POST /vip/order/mock-pay    AUTH  { "orderNo": "<订单号>" }
POST /vip/payment/callback/mock   ANON
     Content-Type: application/x-www-form-urlencoded
     参数：orderNo, amount, channelTradeNo, tradeStatus, sign
     响应：纯文本 "success" / "fail"（不是 JSON 信封）
GET  /vip/center            AUTH
GET  /vip/status            AUTH
GET  /vip/quota             AUTH
GET  /vip/purchase-records  AUTH  ?page=&size=
```

`createOrder` 返回 `data`：`orderNo` / `vipTier` / `orderKind` / `amount` / `payChannel` / `payPayload` / `paymentState` / `orderExpireAt` / `createTime`。

### G2. 回调签名算法（可复现）

来自 `PaymentSignatures`：

1. 取除 `sign` 外的全部参数
2. **丢弃值为空或空串的字段**
3. 键名**升序**排序
4. 拼成 `k=v&k=v&...`
5. `HMAC-SHA256(拼串, secret)`，输出**小写十六进制**
6. 放进 `sign` 字段

`secret` 来自 `forum.payment.mock.secret`，默认 `forum-mock-payment-secret`（以实际 Nacos 配置为准）。

```python
import hmac, hashlib

def sign(params: dict, secret: str) -> str:
    items = sorted((k, v) for k, v in params.items() if k != "sign" and v)
    canonical = "&".join(f"{k}={v}" for k, v in items)
    return hmac.new(secret.encode(), canonical.encode(), hashlib.sha256).hexdigest()
```

> 验签用定长比较，签名长度不对直接判失败——所以伪造签名时长度要凑够 64 个十六进制字符才能真正测到比较逻辑。

### G3. 定价与报价

| 编号 | 前置 | 请求 | 期望 |
| --- | --- | --- | --- |
| VIP-A01 | U-NEW | create tier=1 | `code=0`，`data.orderKind="new"`，`data.amount=3.90` |
| VIP-A02 | U-NEW | create tier=2 | `data.orderKind="new"`，`data.amount=6.90` |
| VIP-A03 | U-FREE | create tier=1 | `data.orderKind="new"`，`data.amount=9.90` |
| VIP-A04 | U-FREE | create tier=2 | `data.amount=15.90` |
| VIP-A05 | U-PRO-30D | create tier=1 | `data.orderKind="renew"` |
| VIP-A06 | U-MAX | create tier=2 | `data.orderKind="renew"` |
| VIP-A07 | U-MAX | create tier=1 | `code=1226` |
| VIP-A08 | 任意 | create tier=0 | `code=1002`（免费档不是商品） |
| VIP-A09 | 任意 | create tier=3 | `code=1002` |
| VIP-A10 | 任意 | create payChannel="alipay" | `code=1224` |
| VIP-A11 | U-NEW | create 时 body 额外塞 `"amount": 0.01` | `data.amount` 仍为 `3.90`（服务端定价，前端传值不生效） |
| VIP-A12 | U-NEW | 连续 create 两次 | 第一单 `DB: payment_state=2`（被关闭），只剩最新一单为 0 |
| VIP-A13 | U-NEW | create 后查 `/vip/center` | 卡片上 PRO 的价格与 VIP-A01 的 `amount` **一致** |

### G4. 升级差价

前置：U-PRO 且存在一条 `price_plan='first_purchase'` 的 PRO 成功流水。按 §D3 把 `vip_expire_at` 设到指定剩余天数后 create tier=2。

| 编号 | 剩余天数 | 定价体系 | 期望 `data.amount` |
| --- | --- | --- | --- |
| VIP-A20 | 30 | first_purchase | `3.00` |
| VIP-A21 | 20 | first_purchase | `2.00` |
| VIP-A22 | 10 | first_purchase | `1.00` |
| VIP-A23 | 3 | first_purchase | `0.30` |
| VIP-A24 | 1 | first_purchase | `0.10` |
| VIP-A25 | 30 | normal | `6.00` |
| VIP-A26 | 20 | normal | `4.00` |
| VIP-A27 | 剩余 < 0.1 天 | 任意 | `≥ 0.01`，不得为 `0.00` |
| VIP-A28 | `vip_expire_at IS NULL`（长期有效） | first_purchase | `3.00`（按满周期收，不得是 `0.01`） |
| VIP-A29 | U-TRIAL（无 PRO 流水），30 天 | 退回首购资格判定 | 有首购资格时 `3.00` |
| VIP-A30 | 单调性扫描：剩余 1~30 天逐日 create | — | 金额随天数**单调不减**，无倒挂 |
| VIP-A31 | 不变量 | — | `3.90 + 3.00 = 6.90` 且 `9.90 + 6.00 = 15.90` |

> VIP-A30 会创建 30 张订单，每次 create 会关掉上一张。跑完后按 §D4 清理。

### G5. 回调验签（**每条都必须验，这是换真实渠道前唯一的失败路径覆盖**）

前置：U-NEW create tier=1 得到 `orderNo`，`amount=3.90`。每条用例后把订单重置回 PENDING 或重新下单。

> 🔴 **本组最大的陷阱——已经害出过一次假通过，务必读完再跑。**
> 接口在**两种完全相反的情况下都返回 `success`**：
> ① 验签通过且发货成功；② 验签通过但业务拒发（金额不符 1228 / 会员状态已变 1227）。
> 后者返回 `success` 是**故意的**——渠道收不到约定应答就会一直重推。
> 所以 **`response=success` 单独不能证明任何事**。凡是期望「发货」的用例（A40、A49、A53、G6/G7 全组），
> **必须查库确认 `payment_state=1`**；凡是期望「拒发」的用例（A42、A66、A67、A70），
> **必须查库确认 `payment_state` 仍为 0**。只断言响应文本的结果一律记为 `BLOCKED`，不得记 `PASS`。
>
> 另注意：回调的 `amount` 要与**该笔订单实际的 `paid_amount`** 一致。拿文档里的示例值 3.90
> 去打一笔 6.00 的升级单，会走进 1228 拒发分支——而它同样回 `success`。

| 编号 | 回调参数构造 | 期望响应 | 期望副作用 |
| --- | --- | --- | --- |
| VIP-A40 | 正确签名，`tradeStatus=SUCCESS`，`amount` **必须等于该订单的 `paid_amount`** | `success` | **必须查库**：`DB: vip_purchase_record.payment_state=1` 且 `paid_at IS NOT NULL`；`user_vip_subscription.vip_tier` 与 `vip_expire_at` 按发货规则改变 |
| VIP-A41 | 把 `amount` 改成 `0.01`，**签名不重算** | `fail` | `DB: payment_state 仍为 0`，未发货 |
| VIP-A42 | 把 `amount` 改成 `0.01`，**用正确密钥重新签名** | `success`（渠道应答仍要给） | **`DB: payment_state 仍为 0`，未发货**（验签过但金额比对失败，1228 分支） |
| VIP-A43 | 换成另一个订单号，签名重算 | `success` 或 `fail` 视订单存在与否 | 原订单 `payment_state 不变` |
| VIP-A44 | 完全不传 `sign` | `fail` | 未发货 |
| VIP-A45 | `sign` 传 64 个 `0` | `fail` | 未发货 |
| VIP-A46 | `sign` 传长度不足的字符串 | `fail` | 未发货 |
| VIP-A47 | 用错误密钥签名 | `fail` | 未发货 |
| VIP-A48 | 正确参数外再注入一个 `extra=1` 字段（不重签） | `fail` | 未发货 |
| VIP-A49 | 参数顺序完全打乱后提交 | `success` | **正常发货**（签名与顺序无关） |
| VIP-A50 | 空参数体 | `fail` | 未发货 |
| VIP-A51 | `amount` 传 `abc`（非法数字），签名重算 | `fail` | 未发货 |
| VIP-A52 | `tradeStatus=FAIL`，签名正确 | `success` | `DB: payment_state=2`（关单），未发货 |
| VIP-A53 | 用 VIP-A40 那份完全相同的回调**再推 2 次** | 两次都 `success` | **前置：A40 必须已真正发货**（`payment_state=1`），否则本条无意义。断言 `vip_expire_at` 与首次发货后**完全相同**，`payment_state` 仍为 1，无第二条成功记录 |
| VIP-A54 | 并发推送 10 份相同回调 | 全部 `success` | 只发一次货 |

### G6. 发货规则

| 编号 | 前置 | 操作 | 期望 |
| --- | --- | --- | --- |
| VIP-A60 | U-NEW | 买 PRO 并支付 | `DB: vip_expire_at ≈ NOW()+30天`（±2 分钟） |
| VIP-A61 | U-PRO 剩 10 天 | 同档续费并支付 | `vip_expire_at ≈ 原到期日+30天`，**不是** `NOW()+30天` |
| VIP-A62 | U-PRO 剩 10 天，配额周期未走完 | 续费并支付 | `quota_period_start` / `quota_period_end` **不变**（提前续费不送额度重置） |
| VIP-A63 | U-PRO，配额周期已结束 | 续费并支付 | 配额周期重新开始 |
| VIP-A64 | U-PRO 剩 10 天 | 升级 MAX 并支付 | `vip_tier=2`；`vip_expire_at` **不变**；`quota_period_end ≤ vip_expire_at` |
| VIP-A65 | U-PRO 剩 3 天 | 升级 MAX 并支付 | `quota_period_end = vip_expire_at`（被到期日封顶，不是 NOW()+30天） |
| VIP-A66 | 下单后、支付前把 `vip_tier` 改成 2 | 支付 | `code=1227` / 未发货，订单仍 PENDING |
| VIP-A67 | 下单升级后、支付前把 `vip_expire_at` 改掉 | 支付 | 未发货（`expected_expire_at` 比对失败） |
| VIP-A68 | 免费档用户 | 支付成功后查 `/vip/quota` | 上限变为 PRO 口径（通用额度 10.9 / Wan 20） |

### G7. 白嫖攻防

| 编号 | 步骤 | 期望 |
| --- | --- | --- |
| VIP-A70 | ① U-PRO-1D 下升级单（金额约 `0.10`）② 不支付 ③ 把 `vip_expire_at` 改成 +30 天（模拟次日续费）④ 回去支付那张单 | **拒绝发货**（`expected_expire_at` 不一致），不得以 0.10 元换到 29 天 MAX |
| VIP-A71 | 下单后把 `create_time` 改成 31 分钟前，再查单 | `DB: payment_state=2`（查单路径会关过期单） |
| VIP-A72 | 同上，改完后**下一张新单** | 旧单被关闭 |
| VIP-A73 | 支付一张已 CLOSED 的订单 | 拒绝，不发货 |
| VIP-A74 | U-B 查询 U-A 的订单号 | `code=1225`，不泄露订单内容 |
| VIP-A75 | U-B 对 U-A 的订单调 mock-pay | `code=1225` |

### G8. 配额

| 编号 | 操作 | 期望 |
| --- | --- | --- |
| VIP-A80 | 免费档查 `/vip/quota` | 通用额度上限 `6.0`，Wan 上限 `15` |
| VIP-A81 | PRO 查 | `10.9` / `20` |
| VIP-A82 | MAX 查 | `20.9` / `50` |
| VIP-A83 | `/vip/center` 三张卡的权益文案 | 与 VIP-A80~82 的数字一致 |
| VIP-A84 | 会员已过期（`vip_expire_at` 设为昨天）查 quota | 回落免费档口径，**不得**仍是 PRO/MAX |
| VIP-A85 | 并发 20 次 `/vip/quota`（触发配额周期滚动） | 全部成功；`DB: quota_period_start` 只被写入一次，无重复滚周期 |
| VIP-A86 | 免费档发起生图 | `code=1164`（免费档生图额度为 0）⚠️ 见 §A2 |

---

## H. 不要自动化的用例

| 分类 | 例子 | 原因 | 处理 |
| --- | --- | --- | --- |
| `FORBIDDEN` 真实计费 | 看板娘对话 / 生图、润色、封面生成、AI 搜索深度调用的**批量或重复**执行 | 真实扣费 | 交人工，或单次执行并记录成本 |
| `FORBIDDEN` 不可逆 | `init-db` / `reset-db` / 迁移脚本、真实 OSS 删除、`_removed/` 搬迁 | 不可恢复 | 交人工 |
| 需人判 | 暗色模式矩阵、文案是否"明确"、大厅是否"闪"、动画、滚动跟随 | 无法机器断言 | 走 `TEST-PLAN.md` 的 `UX-*` |
| AI 输出质量 | 摘要好不好、润色是否通顺、牵线判定是否合理 | 非确定性 | 人工抽样 |
| 概率性 | 抽奖掉率、惊喜奖池 | 需大样本且会消耗库存 | 人工或专项统计脚本 |
| 需要两个真人视角 | 群聊权限的完整交互、观战体验 | 可部分自动化，但断言成本高 | 优先人工 |

**半自动（可写脚本，但断言需人确认）**：WebSocket 连通与重连、MQ 消息是否被消费、SSE 流式分片、traceId 跨服务 grep。这类建议先做「采集」再由人判读，不要让 agent 自己下 PASS/FAIL。

---

## I. 报告格式

### I1. 单条结果

```json
{
  "id": "VIP-A42",
  "status": "FAIL",
  "request": { "method": "POST", "path": "/vip/payment/callback/mock", "body": "..." },
  "expected": "响应 success；DB payment_state 仍为 0，未发货",
  "actual": "响应 success；DB payment_state=1，vip_expire_at 被写入",
  "evidence": { "traceId": "...", "sql": "SELECT ... 的输出", "log": "相关日志片段" },
  "reproducible": true,
  "severity": "P0"
}
```

### I2. 汇总

按 §F/§G 的分组给出 `PASS / FAIL / BLOCKED` 计数，并单独列出：

1. **所有 P0 FAIL**（放最前面）
2. 所有 `BLOCKED` 及其缺失的前置
3. 本轮真实产生的 AI 调用次数与预估费用
4. 执行期间写入过的数据（订单号、帖子 ID、用户 ID），供人工清理

### I3. 什么时候必须停下来问人

- 前置检查（§B2）任一项不通过
- 出现资金相关的 FAIL（重复发货、金额不符、白嫖成功）——**立即停止该组**，不要继续跑后续用例污染现场
- 需要执行本文未授权的写操作
- 契约无法从源码确认
- AI 额度返回 `1164` 或预算超出预期
