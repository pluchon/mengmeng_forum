# Java 后端残留代码审查清单

审查范围：`java-cloud-standalone/` 全部 8 个模块（common、gateway、auth、content、economy、ai、im、game），约 1000 个 Java 文件。
交叉验证范围：`java-cloud-standalone/`、`forum-vue/front/src/`、`ai-server/`、`deploy/`、`scripts/`。

审查结论：确定残留约 134 条，疑似约 56 条。产品口径已于 2026-08-28 全部确认，第三章已转为可执行清单。

**当前状态：代码尚未改动**（仅删除了根级 Postman collection，见文末）。

按域分布：content 31、economy 28、game 25、ai 21、im 19、common 11、auth 9、gateway 0。

---

## 重要前提：Python 侧对 Java 的调用面

本次审查一度存在方法论漏洞——最初只把前端当作 Java 端点的消费方。实际上 Python（AI 服务）也会调用 Java，真正的业务都在 Java 端。已专项复核，结论如下：

**`ai-server` 对 Java 的 HTTP 调用只有一个端点。**

| 项 | 事实 |
|---|---|
| 唯一客户端 | `ai-server/clients/forum_backend_client.py`，文件 docstring 自述「Java 论坛后端 HTTP 客户端（公告中心等只读接口）」 |
| 唯一方法 | `list_published_notices()` → `GET /notice/center/list`（路径可由 `settings.forum.notice_list_path` 覆盖） |
| 复核方式 | 全 `ai-server/` 搜索 `requests.(get\|post\|put\|delete)` / `httpx` / `aiohttp`，逐个确认去向：其余全部指向 DashScope、Tavily、ffmpeg、OSS，无一指向 Java |
| MCP 工具 | `ai-server/mcp/` 下只有 `datetime_tool.py` 与 `tavily_search.py`，不访问 Java |
| 反向调用 | Java → Python 是主要耦合方向（`AiPythonGatewayClient` 的 26 个 intent 组合，已验证全部对齐），另有 RabbitMQ 异步链路 |

**这条事实反过来强化了 1.10 的结论**：`/notice/center/list` 不仅前端在调，Python 也在调（拉公告喂给 AI），该端点与 `ForumNotice` 读取链**绝对不能删**。

### AI 访问 Java 业务的真正通路：域间 Feign 内部契约

Python 直连 Java 只有公告一条，但 **AI 模块访问 Java 业务数据主要靠 Java 域间调用**（ai 域 → content/auth/economy 域的 internal 端点）。这一层已专项复核：

`content/api` 只有 4 个内部契约，全部是只读查询或 AI 生图转存，**不含任何本次待删端点**：

```8:12:java-cloud-standalone/content/api/src/main/java/org/pluchon/forum/api/content/ArticleInternalApi.java
// 帖子域内部契约 纯 API，无 @FeignClient；供 AI 看板娘等跨域回表
public interface ArticleInternalApi {

    @GetMapping("/article/internal/batch")
    List<ArticleInternalVO> listByIds(@RequestParam("ids") List<Long> ids);
```

| 契约 | 用途 |
|---|---|
| `ArticleInternalApi` | 批量查帖、搜索候选、点赞标题、收藏歌曲标题（注释自述「供 AI 看板娘等跨域回表」） |
| `FileInternalApi` | `/file/internal/upload-ai-generated`，AI 生图转存 OSS |
| `UserEngagementInternalApi`、`FavoriteFolderInternalApi` | 互动数据与收藏夹只读 |

复核方式：在全部 `**/*Feign*.java` 中搜索本次待删的 15 个端点标识符（`publishArticle`、`streamGuide`、三个 upload、`setMascotModel`、`listPublicModels`、`ragVectorSearchUsers`、`validateImagePayload` 等），**零命中**。

**结论**：待删端点全部是面向前端的 public 端点，既无 Python 调用方，也不在任何域间 Feign 契约中。另已扫描 `deploy/`、`scripts/`、`.github/`，无引用。

> 一个重要推论：AI 若要主动删除违规内容，走的**不是 HTTP 端点**，而是 MQ 审核回调链路（详见 3.4）。

---

---

## 数据库实证核查（2026-08-28，本地开发库）

静态搜索定不了的几个问题，已用真实数据敲定。环境：`forum-mysql-dev` 容器（healthy），6 个业务库。

### 核查结论一览

| 对象 | 数据 | 结论 |
|---|---|---|
| `ai_usage_daily` 四个日计数列 | 7 行中 `advanced_llm_used` 合计 5、`image_normal_used` 合计 1，**但最后一次写入是 2026-08-13 15:00** | 存量是**历史遗留数据**，当前无写入方，判定成立 |
| `ai_usage_daily.cover_hint_used` | 合计 13，**最新写入 2026-08-23**，持续在写 | **仍是活跃写入**，与四个死列不同，见下方修正 |
| `forum_ai_long_term_memory` | **0 行** | 3.9 那套 `memories` CRUD 的目标表，从未写入过 |
| `forum_mascot_memory` | **1 行** | 看板娘实际在用的表，两者确为不同表 |
| `forum_outbox_message` | 只有 `forum.notify.message` / state=1 / 33 行；**`forum.notify.reply` 零行** | 2.6 的删除前置条件已满足，可安全删除死分支 |
| `vip_quota_bonus_grant` | 14 行，`qwen_used_micros` / `wan_used_credits` / 两个 reserved 列**全为 0** | 1.6 判定成立，礼包额度从未被消费过 |
| `vip_purchase_record` | 0 行 | 与 1.2 的 mock 口径一致 |
| `forum_vip_quota_config` | `daily_count` 2 行（均为 `image_normal`）、`token_period` 2 行（均为 `token_qwen_deep`） | 确认 3.8：只有生图是 daily_count；`readDailyBucket` 的 `qwen_flash` / `advanced_llm` / `companion_normal` 三个分支**在数据层面也不存在**，是双重死代码 |
| `exam_question` | 0 行 | 2.4 的跨域越界实体可安全删除 |
| `forum_notice` | 6 行 | mock 数据在库，前端与 Python 都在读，保留 |

### 两处前提修正（执行中被实践证伪）

**一、这个项目没有 Flyway。** 本文档多处写「走 Flyway 前向迁移」，实际上全仓库搜 `flyway` / `liquibase` 零命中，也不存在 `db/migration` 目录。schema 由各模块 `src/main/resources/db/create.sql`（mysqldump 风格全量建表脚本）管理。因此列删除的正确做法是**改 `create.sql` + 对开发库执行 `ALTER TABLE`**，两者必须同步，否则新环境与既有环境会漂移。

这条前提错误差点造成一次线上故障：`user_profile_change_request.content_hash` 是 `varchar(32) NOT NULL` 且**无默认值**，如果按原计划「Java 字段现在删、迁移留到最后阶段」，中间每一个提交都会让资料审核提交直接失败（MySQL 严格模式下 `Field 'content_hash' doesn't have a default value`）。所以 3.12 的三列已随 Java 字段删除在**同一提交**内落地。

**二、`GobangRoomParticipantVO.joinedAtMs` 不是死字段，判定有误。** 2.7 把它列进「三游戏复制粘贴死字段」，但 `GobangRoomServiceImpl.buildSpectators` 用它排序观战席（`spectators.sort(Comparator.comparing(GobangRoomParticipantVO::getJoinedAtMs))`），删掉会直接编译失败。已跳过保留。同批的 `sentAtMs` / `serverNowMs` / `baseScore` / `winLine` / `occurredAt` 等确认前后端零读取，已删。

### 一处表述修正：`ai_usage_daily` 不能整表下线

我此前建议「连整表一起下线」，数据显示这个建议**不成立**。该表的六列要分开看：

- **四个日计数列**（`qwen_flash_used` / `advanced_llm_used` / `image_normal_used` / `companion_normal_used`）：写入在 2026-08-13 停止，此后只有读取。时间点与配额迁移吻合——那批写入方正是 commit `5db3f89b` 中作为无引用方法删除的六个 Mapper SQL（`incrementAdvancedIfBelow` 等）。**这四列可删**。
- **`cover_hint_used`**：`incrementCoverHint` 持续在写（最新 08-23，累计 13 次），只是**没有任何读取方**。属于活跃的纯审计写入。

因此 3.8 的处理方式调整为：**删四个死列及其读取链，保留 `ai_usage_daily` 表与 `cover_hint_used` 列**。丢弃一个仍在写入的审计字段没有收益，后续要么给它补一个查询出口，要么等确认不需要审计时再单独下线。

---

## 阅读顺序建议

数量不是重点。第一章的「功能失效」是伪装成死代码的真实缺陷，删掉会把 bug 永久盖住。第二章是零风险清理。第三章是已决策的执行清单。

---

# 第一章　功能失效（禁止当死代码删除）

## 1.1 看板娘积分付费入口彻底失效（ai 域）· 待修

| 项 | 内容 |
|---|---|
| 现象 | 前端「改用萌萌币扣费」入口永不出现；配额百分比气泡恒显示 0% |
| 链路 | `MascotDock.js` → `getMascotQuotaHint` → `/mascot/quota-hint` → `MascotController:75` → `MascotServiceImpl.quotaHintForLlmRoute:248` |
| 根因 | 第 254 行读 `AiUsageDaily.getAdvancedLlmUsed()`，该列全仓库零 SQL 写入方 → `used` 恒 0 → `percent` 恒 0 → 第 260 行 `setCanUsePointsPay(percent >= 95)` 恒 false |
| 前端依赖 | `MascotDock.js:178 / 1343 / 1348` 三处用 `canUsePointsPay` 控制入口显示 |
| 附带说明 | `usePointsBilling` 扣费路径本身是活的，手动传参仍可扣费，但用户无任何 UI 途径触达 |

**修复方案**：economy 域 `VipCenterServiceImpl:120` 已有一份**同名方法** `quotaHintForLlmRoute`，走 `panel.getQwenBudgetMicros()` / `getQwenUsedMicros()`（数据源是活的），且已挂在 `VipInternalApi` 的 `/vip/internal/{userId}/quota-hint` 契约上。把 ai 域 `MascotController` 转发过去即可，逻辑无需重写。

需统一两处口径差异：阈值 ai 侧 95% vs economy 侧 100%；`quotaLabel` ai 侧「Qwen 深度写作」vs economy 侧「通用额度」。

> **与 3.8 强协同**：3.8 已决定下线逐项配额表，届时 ai 域本地读 `ai_usage_daily` 的这条路径本就要删，转发 economy 正好是同一次改动的自然结果。两件事一起做。

## 1.2 VIP 首购优惠对所有用户恒定可用（economy 域）· 已确认 mock · 不处理

> **口径**：支付渠道未接入，当前只做 mock 展示。以下现象属预期行为。

- 任何用户任何时候都能看到首月优惠价 3.9 / 6.9（`VipCenterServiceImpl:109` 的 `selectCount(...) == 0` 恒为 true → `firstPurchaseEligible` 恒成立）
- `/vip/purchase-records` 永远返回空分页，前端 `VipSubscribeDialog.vue:218` 的历史列表永不显示
- 机制：`vip_purchase_record` 表零写入方，`VipPurchaseRecordMapper` 是空的 `BaseMapper`

**保留清单**（支付接通后的写入落点，勿删）：`VipPurchaseRecord` 实体、`VipPurchaseRecordMapper`、`VipPurchaseRecordConverter`、`VipCenterServiceImpl.purchaseRecords`、`/vip/purchase-records` 端点、`UserVipSubscriptionMapper.updatePaidSubscription`、`VipSubscribeResultVO`、`VipSubscribeDTO`。

> **技术债登记**：mock 期间首购优惠对所有人可见可接受，但**支付接通当天必须同步补上 `vip_purchase_record` 的 insert**，否则优惠价会被无限次复用。

## 1.3 头像 VIP 彩色环下线 · 已确认为下线任务

> **口径**：彩色环本就是要删除的对象，此前未删净。只保留自己界面下首页顶部的 PRO 会员提示。

### 要保留的部分（数据源已核实正确）

`vip-status-pill` 那个 PRO 提示由 `composables/useVipStatusEntry.js` 驱动，它**不读** auth 快照，而是调 economy `/vip/status`（`user_vip_subscription` 权威表）：

```1:13:forum-vue/front/src/composables/useVipStatusEntry.js
// 顶栏会员入口：档位以 economy /vip/status 为准 user_vip_subscription ， 不能依赖 auth.user 快照 开通后可能长期不同步
export function useVipStatusEntry(userStore) {
```

且 `applyStatus`（第 37-43 行）会把权威档位**回写** `userStore.vipTier`。使用方：`TheHeader.js`、`HomeTopBar.js`、`MusicHallPage.js`、`DoorPortal.js`。

保留：`useVipStatusEntry.js`、`utils/vip.js` 的 `isVipActive`、`constants/vipStatusIcons.js`、`/vip/status` 端点与 `VipStatusVO`、`vip-status-pill` 样式。

### 要删除的部分

**前端：`UserAvatarVip` 的 VIP 环能力及全部 `vip-tier` / `vip-expire-at` 传参**（17 个文件约 30 处）

| 文件 | 行号 | 备注 |
|---|---|---|
| `components/common/UserAvatarVip.vue` | 组件本身 | 删 `vipTier` / `vipExpireAt` / `showVipRing` 三个 prop 与环渲染，退化为普通头像 |
| `views/MessageView.vue` | 107/180/199/255/549/774/931/1224 | 8 处，最集中；另删辅助函数 `bubbleVipTier` / `bubbleVipExpireAt` |
| `views/ArticleDetail.vue` | 207/232/511/519 | |
| `views/Profile.vue` | 28/310/521 | 另删 `displayVipTier` / `displayVipExpireAt` |
| `components/article/SubReplyArea.vue` | 31/39 | |
| `components/search/SearchUserRow.vue` | 13 | |
| `components/search/SearchArticleCard.vue` | 55 | |
| `components/user/UserFollowListDialog.vue` | 38 | |
| `components/settings/BasicInfo.vue` | 9 | |
| `components/emoji-shop/EmojiShopDetailDialog.vue` | 69 | 另删 `uploaderVipTier` |
| `components/mascot/MascotDock.vue` | 169 | 另删 `ringVipTier` |
| `components/layout/TheHeader.vue` | 67 | 环当前是**开启**状态 |
| `components/layout/HomeTopBar.vue` | 69 | 已 `show-vip-ring="false"`，只需删传参 |
| `views/DoorPortal.vue` | 84 | 同上 |
| `views/MusicHallPage.vue` | 51 | 同上 |
| `views/HomeFeed.vue` | 179 | 同上 |
| `assets/styles/home.css` | 1116 | `.home-vip-badge-img` 孤儿样式类，模板零引用 |

**后端**：`UserBriefVO.java:21-22` 的 `vipTier` / `vipExpireAt`（`from()` 本就没赋值）、`UserFollowServiceImpl:379` 全参构造对应实参、economy emoji shop 详情的 `uploadUserVipTier` / `uploadUserVipExpireAt`（`EmojiShopServiceImpl:1100-1103`）。

### 不在本次范围

auth 域对 `user.vip_tier` 的 5 条读取链路属 1.4，**不要动**——`AiQuotaServiceImpl:46` 与 `MascotServiceImpl:241` 还在用 `AuthenticatedUser.getVipTier()` 做额度与权限判定，那是功能性用途。

### 执行顺序

**必须先前端后后端**。反序会导致前端拿到 `undefined` 但环仍在渲染。前端改完 `npm run build`，再按深色模式状态矩阵走一遍他人视角页面（搜索结果、信息流、消息列表、帖子详情、关注/粉丝弹窗）。

## 1.4 user 表 VIP 快照列无写入方（auth 域）· 单独立项

`user.vip_tier` / `vip_expire_at` 全仓库零 SQL 写入（写入只在 economy 的 `user_vip_subscription`，建表注释已标注「权威在 economy」），但 auth 域 5 条读取路径仍当有效数据用：

| 读取点 | 去向 |
|---|---|
| `UserServiceImpl:607` | 写入 Redis 用户详情 Hash（把陈旧值固化进缓存） |
| `UserServiceImpl:663` | 从 Redis Hash 反序列化回 `User` |
| `UserAuthSnapshotServiceImpl:41` | 回源 DB 补全鉴权字段 |
| `AuthLocalSnapshotResolver:35` | 注入 `AuthenticatedUser` 鉴权主体 |
| `UserConverter:30` | 填入 `UserSessionVO` 直接返回前端 |

**风险高，单独立项。** `AuthLocalSnapshotResolver` 是拦截器鉴权主体，`AiQuotaServiceImpl:46` 与 `MascotServiceImpl:241` 依赖 `getVipTier()` 做额度与权限判定。切换数据源到 economy 时若漏掉任一链路，会造成 VIP 权益静默失效。

> 1.3 完成后本项难度下降：`UserSessionVO.vipTier` 仅剩 `userStore` 初始值使用，而该值随后会被 `useVipStatusEntry.applyStatus` 覆盖成权威值。

## 1.5 会员中心 daily_count 配额恒显示 0 · 随 3.8 决策解决

`ai_usage_daily` 的 `qwen_flash_used` / `advanced_llm_used` / `image_normal_used` / `companion_normal_used` 四列零写入方（`ensureUsageRow` 只插全 0 占位，`incrementCoverHint` 只写 `cover_hint_used`）。

读取链路：`AiUsageInternalService.usageSnapshot:43` → `AiUsageDailyBucketsVO` → economy `VipCenterServiceImpl.readDailyBucket:427` → `toItem` 的 `daily_count` 分支。

后果：会员中心展示「Wan 生图（普通）已用 0 次 / 15 次，0%」，而**同一响应**里的 `wanImageUsed`（走 `forum_ai_usage_log`）反而准确——同一份数据两个矛盾口径。

**3.8 已决定下线逐项配额表，本项随之消失**，无需再改造数据源。

## 1.6 VIP 礼包额度预占链路从未被调用（economy 域）

`VipQuotaBonusServiceImpl` 的 `reserve` / `settle` / `release` 是 `vip_quota_bonus_grant` used/reserved 列唯一写入方，唯一入口是 `VipInternalController` 三个内部端点。ai 域与 im 域 Feign 客户端虽 `extends VipInternalApi` 继承了签名，但**零行调用**（ai 域走自己的 `forum_ai_quota_period_usage`）。

> **决策：登记技术债，本次不动。** 数据核查确认 14 条礼包记录的 `qwen_used_micros` / `wan_used_credits` 与两个 reserved 列全为 0，从未被消费。真正修好等于实现一套「礼包额度独立于基础额度结算」的机制，属新增功能而非残留清理，不在本次范围。`buildQuotaPanel` 的恒等变换与 2.4 列出的 5 个礼包契约类**一并保留**，作为将来接通的落点。

于是 `buildQuotaPanel:314-340` 是恒等变换：

```287:345:java-cloud-standalone/economy/server/src/main/java/org/pluchon/forum/service/impl/vip/VipCenterServiceImpl.java
        long qwenBonusUsedMicros = bonusGrants.stream()
                .mapToLong(item -> Math.max(0L, nvl(item.getQwenUsedMicros())))
                .sum();
        long baseQwenUsed = Math.max(0L, observedQwenUsed - qwenBonusUsedMicros);
        long qwenUsed = baseQwenUsed + qwenBonusUsedMicros;
```

先减后加的空操作，掩盖了「礼包额度本应独立结算」的设计意图。

## 1.7 game 域四类 WebSocket 错误消息被静默丢弃

后端推送、前端零命中：`room_error`（三个 RoomWebSocketHandler + `JinziRoomServiceImpl` + `TetrisRoomServiceImpl`）、`game_error`（三个 GameWebSocketHandler）、`move_rejected`（`GobangRoomServiceImpl` 等）、`garbage_received`（`TetrisRoomServiceImpl`）。

后果：房间不存在、消息类型不支持、落子参数为空、被攻击加垃圾行，界面无任何反馈。`room_error` 尤其糟——后端发完紧接着 `session.close(POLICY_VIOLATION)`，用户视角是「连接莫名断开」。

应补前端分支，而非删后端推送。

> 对比：im 域 9 种消息类型前端逐一覆盖无遗漏，是对齐的正面样本。

## 1.8 im 域 ReceiverExistsGuard 永远放行

```20:29:java-cloud-standalone/im/server/src/main/java/org/pluchon/forum/service/impl/message/guard/ReceiverExistsGuard.java
    @Override
    public MessageSendGuardResult check(MessageSendContext context) {
        userLookupService.queryUserByUserId(context.getReceiverUserId());
        return MessageSendGuardResult.pass();
    }
```

返回值被丢弃，无条件 pass。`ImUserLookupService.queryUserByUserId` 对 null / 非正 id 返回 null，否则透传 Feign 结果，全程不抛异常。且 `supports()` 返回 `true`，意味着**每条私信都白跑一次跨服务调用**。

删除前必须确认「给不存在的 userId 发私信」靠什么拦截。若无其他拦截点，正确修复是补 null 判断抛 `FAILED_USER_NOT_EXIST`。

## 1.9 群聊表情收藏来源被静默丢弃（im 域）

`FavoriteEmojiRequest.originGroupMessageId` 全仓库只有两处：DTO 声明与前端 `stores/chatEmoji.js:130` 赋值。后端零读取，`MessageServiceImpl.favoriteEmoji` 只处理 `originMessageId`，`UserChatEmoji` 实体只有 `origin_message_id` 一列。删字段会掩盖缺口。

> **决策：登记技术债，本次不动。** 修好需要给 `user_chat_emoji` 增列存群聊来源，属 schema 扩展而非残留清理。DTO 字段与前端赋值**一并保留**，保持缺口可见——删掉反而会让「群聊表情收藏丢来源」这个缺陷失去线索。

## 1.10 公告读取链必须保留（im 域）· 已确认 mock

> **口径**：公告目前是前端 mock，后续做管理端时再接上。

写入侧确实不存在（全仓库除建表 SQL 与一条 demo seed 外，无任何创建/编辑公告的 Controller 或 Service）。

**但读取侧有两个消费方**，`ForumNotice` 实体、`ForumNoticeMapper`、`ForumNoticeReadServiceImpl`、`ForumNoticeCenterItemVO`、`/notice/center/list` 端点**绝对不能删**：

1. 前端公告中心
2. **Python**：`ai-server/clients/forum_backend_client.py` 的 `list_published_notices()`（这是 Python 对 Java 唯一的 HTTP 调用）

`ForumNotice.sort` 字段随整个模块保留（管理端接入时可能需要排序）。

## 1.11 AI 价格缓存永不失效（ai 域）

`AiPointsBillingService.priceCache` 靠双检锁懒加载，唯一刷新入口 `refreshPriceCache()` 跨仓库零调用，且无 `@Scheduled` / `@EventListener` / `@PostConstruct`。修改 `forum_ai_model_price` 必须重启服务才生效。

**按「其他按建议来」处理**：这是「缺一个刷新钩子」而非「多一个方法」。建议**保留方法**并补一个定时刷新或缓存 TTL，而不是删掉它。

## 1.12 两个 AI 功能漏进计费白名单（ai 域）

`AiHubServiceImpl` 中 `music_recommend`（:203）与 `music_ai_search`（:227）走 `billBatch`，但 `BillableFeature` 白名单只有 `ARTICLE_TAG_RECOMMEND` → `contains` 返回 false → 这两个功能实际免费，`billable_state` 落库为 0。

> **决策：保持免费，不改代码。** 补白名单会让这两个功能开始扣萌萌币，属收费口径变更而非技术修复。现状（免费 + `billable_state=0`）即为当前生效口径，代码无需改动。将来若要收费，只需把这两个 feature 加入 `BillableFeature` 白名单，计费链路本身是活的。

---

# 第二章　零风险清理清单（可直接动手，零行为变更）

## 2.1 common 域

| 目标 | 说明 |
|---|---|
| `ImageCompressor.compressForAudit`（:69） | 审图改传 OSS URL 后废弃的 base64 压缩。连带删 `ForumBusinessConstants.IMAGE_AUDIT_COMPRESS_TARGET_BYTES` / `MAX_DIMENSION` 及 `Constant` 两个别名，闭环死链。**同文件 `compress()` 是活的，私有常量 `QUALITY_STEPS` 被它共用，不可删** |
| `UserDerivedCacheInvalidator.invalidateUserCachesNow` | 与 `invalidateUserCaches` 删同两个 Redis key，区别仅是没包在 `TransactionHooks.afterCommit` 里。留着是违反「只在事务提交后失效缓存」约定的陷阱 |
| `ForumDomainNames.AI`（:10） | 五个常量中唯一零引用 |
| `OssPaths.COMPANION_AI`（:25） | 零引用，但在 `allBusinessPaths()` 里，导致每次启动在 OSS 创建一个永无写入的空目录 |
| `Result(String)`、`Result(int)`、`success(ResultCode)` | 全项目零处外部 `new Result(...)`。`Result(String)` 能构造 `code=0` 的畸形响应，删除有正收益。**`@NoArgsConstructor` 必须保留（Jackson 需要）** |
| 收窄可见性 | `JWTUtils.genJwt`、`MD5Utils.md5Common`、`OssConfig.publicUrlPrefix`、`ForumDateTimes.ZONE_SHANGHAI`、`OssPaths` 三个 ROOT 常量、`InternalApiConstants.INTERNAL_PATH_MARKER`、`UniformCaptchaImageResourceProvider.WIDTH/HEIGHT`。`genJwt` 尤其应收窄——绕过 `genJwtForUser` 直接建 token 会漏掉 `tv`（tokenVersion），导致该 token 无法被版本吊销机制作废 |

## 2.2 auth 域

| 目标 | 说明 |
|---|---|
| `MailCodeService.getForReset` / `SMSCodeService.getForReset` | 含两个实现与私有 `getInternal`。注释自述「仅开发排查」，重置流程已走 `consumeResetCode`。**安全隐患**：接上 Controller 就等于明文吐出重置验证码 |
| `AuthTokenService.issueToken` | 已被 `issueLoginToken`（3 个调用方）取代 |
| `UserFollowListItemVO.followsProfileUser` | 只写不读，写入的还是调用方已知的常量 |
| `UserFollowService.isFollowing` | 仅 `UserFollowServiceImpl:114` 内部调用，移除接口声明降为 private |
| `UserAuthSnapshotSupport` | 注释称「本地/远程实现共用」但远程实现从不存在。可内联回 `UserAuthSnapshotServiceImpl` |
| `UserProfileChangeServiceImpl.approve` | `protected` 无子类无外部调用，用的是 `TransactionTemplate` 不需代理，降 private |

**收益最高的一条不是删除**：`FollowInternalController` 没有 `implements FollowInternalApi`，而是手抄了四条路径。当前字面一致所以线上是通的，但任何一侧改动编译器都不会拦。同目录 `UserInternalController:19` 是规范写法。应补上 `implements` 并移除方法上重复的路径注解。

## 2.3 content 域

| 目标 | 说明 |
|---|---|
| `ArticleMapper.sumEngagementForWorkbench` | 唯一零调用的 Mapper 自定义 SQL。旧工作台聚合残留 |
| `ArticleUserMusicService.findByMusicKeys` / `listPublished` | 已被 `listPublishedWithAiProfile`（3 个调用方）取代 |
| `UserMusicConverter.overlayPublished` | 全仓库唯一出现处就是声明行 |
| `LotteryImagePathUtils.activityCoverRelative` | 同类其余方法在用，仅此相对路径版本零引用 |
| `SearchServiceImpl.extractRankedIds` | 已被 `extractArticleHitIds`（5 处）与 `extractUserHitIds` 取代 |
| `SearchServiceImpl.USER_AI_HYBRID_MIN_SCORE` | 兄弟常量均在用，说明用户搜索 hybrid 阈值分支已被移除 |
| `Article.auditNotifyEmail`、`UserMusic.audioFingerprint` | 四路搜索全零，见 3.12 |
| 收窄接口声明（5 条） | `ArticleTagService.listForBoard`、`CategoryService.queryAllCategories`、`ArticleSearchIndexService.indexPublishedArticle`、`ArticleVideoTranscodeService.processTranscode`、`ContentUserLookupService.isMuted`。实现仍活（Impl 内部自用），删声明 + `@Override`，不删方法体 |

`GET /board/selectBoardByBoardId`、`GET /board/selectBoardBy`、`GET /articleQuestion/acceptedAnswer`、`GET /article/getHotArticleList` 四个端点前端零调用（已确认无 Python 调用方）。删除时同步清理 `common/AuthApiPaths.java` 的网关白名单条目。

> `ArticleService.getHotArticleList` 只是转发方法，`ArticleHotRankingService.getHotArticleList` 是活的（`RecommendationServiceImpl:582`），勿连坐。

## 2.4 economy 域

| 目标 | 说明 |
|---|---|
| `ExamQuestion`、`ExamQuestionMapper` | **跨域越界**：考试题库实体错放在 economy，零引用。对应三张表也建在 economy 的 `create.sql` |
| `LotteryActivityPrizeMapper.selectPool` | 被 `selectDrawablePool`（多 `catalog_status = 1` 条件）取代 |
| `LotteryDrawRecordMapper.countDistinctDrawUsers` / `selectByUserAndBatchKeys` | 前者统计口径废弃，后者批量版被单条版取代 |
| `EmojiShopServiceImpl.querySemanticShopList` / `containsControlChar` | 前者被 `querySemanticShopListByIds` 取代 |
| `LotteryServiceImpl.buildDrawRecordRows` | 全仓库仅声明 |
| `VipQuotaBonusServiceImpl.value(Integer)` | 文件内 6 处调用全传 `Long`，该 int 重载零命中 |
| VIP 礼包契约类（5 个） | `VipBonusReserveRequest`、`VipBonusSettleRequest`、`VipBonusReleaseRequest`、`VipBonusReservationVO`、`VipBonusSettlementVO`，仅服务于 1.6 死链 |

游标分页整链（见 2.8）：`GET /points/log/cursor`、`GET /checkin/log/cursor`、`GET /points/daily`、`GET /points/log` 前端零调用，前端已改用 `/points/center/log`。

> **按 1.2 口径保留**：`VipSubscribeResultVO`、`VipSubscribeDTO`、`UserVipSubscriptionMapper.updatePaidSubscription`。

## 2.5 ai 域

**收益最大**：AI 任务会话 handoff 功能簇 8 条，闭环互引，无任何 Controller / Feign / 前端 / Python 触及——`AiWorkspaceService.handoff` / `currentTask` / `finishTask` 及实现、`AiTaskHandoffRequest`、`AiTaskSessionVO`、`AiWorkspaceConverter.toTaskSessionVO`、`ForumAiTaskSession` 实体与 Mapper、`AiTaskMode` / `AiTaskState` 枚举，连带私有 `findActiveTask`、`normalizeTaskMode`。

| 目标 | 说明 |
|---|---|
| `ragVectorSearchUsers` 全链路 | 契约 + Controller + Service + 实现，无任何域包装它。连带常量 `USER_SEARCH_MIN_SCORE` |
| `validateImagePayload` 全链路 | 方法自带 `@deprecated 上传审图请用 image-url`。连带 `AiImageModerationRequest` 与私有 `hexHead` |
| `AiPointsBillingService.charge` | 已被 `bill(...)` 六参重载取代 |
| `MascotServiceImpl.resolveImageQuality` | 方法体只有 `return "normal";`，两个入参全未使用，内联即可 |
| `AiQuotaServiceImpl.hasAdvancedQwenAccess` | 实现为 `user != null && user.getId() != null`，恒 true |
| 死字段 4 个 | `AiPolishRequest.kind`、`MascotChatRequest.llmProvider`（注释自述已废弃）、`MascotChatRequest.imageQuality`、`ForumAiModelPrice.vipOnly` |

> 前端仍在发送三个被后端忽略的字段：`llmProvider`、`imageQuality`（`MascotDock.js:1801/1803`）、`AiPolishRequest.kind`。删 Java 字段时同步清前端。

## 2.6 im 域

| 目标 | 说明 |
|---|---|
| 站内信列表分页整链 | `GET /message/selectMessageListByUserIdWithPage` + Service 方法 + 实现 + `MessageListResponse`。**附带规范收益**：该 VO 直接暴露 `Message` 实体，违反「Controller 只返回 VO」 |
| `GroupChatServiceImpl.appendSystemMessage` | 全仓库仅定义行，已被 `sendSystemMessage` 取代 |
| `ChatMessageReportServiceImpl.safeRetryCount` | 重试计数改为调用点内联 |
| `MessageSendGuardChain.defaultChain` | 见 2.8 |
| 枚举清理 | `OutboxMessageState.CONSUMED` / `FAILED` / `fromCode` / `getLabel`（状态机实际只用 PENDING/SENT/DEAD，`ForumOutboxMessage.messageState` 注释与实现不符需同步修正）、`GroupChatStatus.fromCode`、`GroupChatMemberRole.fromCode`、`GroupChatJoinRequestStatus.fromCode`、`MessageStatus.toString` |
| `TextContentGuard` 的 ALBUM 分支 | `supports()` 声明支持 ALBUM，`check()` 第一行就 `return pass()`。从 `supports()` 去掉 ALBUM，行为不变 |
| `WebSocketPushService.pushLocal` | 仅本文件三处，收窄 private |
| `ImUserLookupService.getUserInfoById` | 方法体只是 `return queryUserByUserId(userId);`，同类两个公开方法做同一件事 |

**需先查库**：`OutboxDispatchTask` 为 `ROUTING_KEY_QUEUE_1`（`forum.notify.reply`）保留了分支，但 im 侧唯一 enqueue 点固定传 `QUEUE_2`。若生产库仍有历史待投递行，删分支后会撞 `IllegalArgumentException`，重试 5 次进死信：

```sql
SELECT routing_key, count(*) FROM forum_outbox_message WHERE message_state = 0 GROUP BY routing_key;
```

## 2.7 game 域

| 目标 | 说明 |
|---|---|
| `GameUserProfileMapper.applyWin` / `applyLose` / `applyDraw` | 固定分值算分被动态 Elo 取代。**同文件 `updatePlayStatus`、`applyTetrisFinish` 仍有调用方，勿连坐** |
| 算分常量 4 个 | `GameConstants.SCORE_DELTA` / `JINZI_SCORE_DELTA` / `TETRIS_PK_SCORE_DELTA` + `TetrisRoomServiceImpl.PK_SCORE_DELTA`（同值副本） |
| `GameConstants.END_ABNORMAL` | 结束原因实际只用 FIVE/LINE/DRAW/SURRENDER/DISCONNECT/TIMEOUT |
| `GameMatchRequest` | 匹配请求已由 `GameWsMessage.type` 表达 |
| `GameConnectionRegistry.sendToLobby` | 大厅推送统一走事件总线 |
| `GameRankRules.gobangAiWeighted` | 被 `GameRankServiceImpl.weighted` 取代 |
| `GameAiPlanner.isAiTurn` | 实际直接比 `GameConstants.AI_USER_ID` |
| `TetrisScoreValidator.resolveForumPoints` | 论坛积分发放在 economy 域 |
| `GobangActionContext.getChatRequest`、`TetrisBlock.getRotateIndex`、`GobangAiDifficultyProfile.scoreBucket` | 手写方法零引用 |
| `GobangRoomServiceImpl.countChess` | 与 `countMoves`（3 处调用）功能重叠 |
| 三游戏复制粘贴死字段 | `sentAtMs`（三份 ChatVO）、`serverNowMs`（三份 RoomStateVO）、`GobangRoomParticipantVO.joinedAtMs`、`GameMatchBucket.baseScore`、`GameRankSettlementResult.ranked`、`GameRankPlayerChange` 四个前后分字段、`GameFinishedMqVO.winLine` / `occurredAt` |

**房间快照子系统只写不读**：`saveSnapshot` 两个写入方（`GobangRoomServiceImpl:541`、`JinziRoomServiceImpl:623`），`getSnapshot` 零调用。断线重连已由 `GameRoomStateCacheService.getState` 承担，用**不同的 key 前缀**。每局都在 Redis 写一份带 TTL 的 JSON 给没人看，Tetris 从未接入。

**整套下线**（接口 + `RedisGameRoomSnapshotServiceImpl` + `GameRoomSnapshotVO` + 两处写调用 + 随之无引用的 `GameRedisKeys.roomState`），不能只删 `getSnapshot`。

## 2.8 跨域重复模式

**四个永不生效的 guard chain 注入缝**。均为 `@Autowired(required = false)` setter，对应类型不是 Spring Bean 或 Bean 必然存在，else 分支永不进入。本意给单测预留，但 game / economy / im 三域均无 test 源目录。

| 位置 | 情况 |
|---|---|
| `GobangRoomServiceImpl.setGobangGuardChain` | `GobangGuardChain` 无 `@Bean` / `@Component` 提供 |
| `GobangMatchServiceImpl.setGobangMatchGuardChain` | 同上 |
| `LotteryServiceImpl.setLotteryDrawGuardChain` | 只通过 `defaultChain()` 静态工厂创建 |
| `MessageSendGuardChain.defaultChain`（im） | 本身是 `@Component` 故 Bean 必然存在；且硬编码的 Guard 列表会与 `@Component` 自动收集的实际链路漂移 |

**游标分页整链已死**。`CursorPageResult.nextCursor` 有两个写入方（`PointsServiceImpl:227`、`CheckinServiceImpl:546`），但全仓库零读取方，前端连这个词都没出现过，且**根本没调用过** `/log/cursor` 两个端点。`CursorUtils` + `CursorPageResult` + 两个路由 + 两处实现构成完整死链。`PointsController:54` 的 Swagger 描述还在描述一个不存在的用法。

> `CursorPageResult` / `CursorUtils` 位于 common，下线前确认其他域无使用。

**`ResultCode` 15 个从未使用的枚举常量**，暴露三次功能下线未清理：VIP 积分订阅（1166、1198）、弹幕隐藏（1188/1189/1190）、关注幂等化（1170/1171）。`FAILED_VIP_SUBSCRIBE_UNAVAILABLE` 的文案本身就是「积分订阅已下线」的用户提示。

建议删枚举项但在文件留注释记录废弃码段，**不要复用这些码位**。完整列表：1105、1107、1108、1121、1122、1126、1157、1166、1170、1171、1188、1189、1190、1198、2001。

---

# 第三章　执行清单（产品口径已确认）

## 3.1 删除旧改密端点 · 决策：删

> **口径**：明确不提供「输入旧密码改新密码」，有风险。

删除范围：`UserController.java:108-114` 的 `PUT /user/modifyPassword`、`UserService.updatePawssword:38` 声明、`UserServiceImpl:537-563` 实现（方法名那个 `Pawssword` 拼写错误随之消失）。

保留：`/user/findPasswordByMail`、`/user/findPasswordBySms`（前端 `api/settings.js:37/45` 在用）。

## 3.2 删除 Postman collection 与看板娘模型设置端点 · 决策：删

**已完成**：根级 `luntan.postman_collection.json`（311 KB）已删除。

> 附带影响：项目规则中「覆盖的接口同步进根级 Postman collection」这一条随之失效。接口清单可依赖已有的 springdoc/Swagger。

待删除：`UserController.java:116-125` 的 `POST /user/setMascotModel`、`UserService.setMascotModel:36`、`UserServiceImpl:527-531`。它是转发到 ai 域的薄封装，前端走 ai 直连，且 3.11 已确定不做皮肤选择页。

执行要点：删除后检查 auth 域的 `MascotPreferenceInternalFeignClient` 是否还有其他用途，若无则一并清理。

## 3.3 删除旧同步发布链 · 决策：删

删除范围：`ArticleController.java:87` 的 `PUT /article/publishArticle`、`ArticleService.publishArticle:25`、`ArticleServiceImpl:176`，以及前端孤儿封装 `api/article.js:40-42` 的 `publishArticle()`。

**这是安全收益**：该端点能把帖子直接置为已发布、绕过审核链。现行流程是 `createDraft` → `submitForAudit` → MQ 回调 → `ContentForumConsumer.handleArticleAuditResult` → `applyAuditResult`。

执行要点：属写操作链路且涉及状态流转，删后在浏览器完整走一遍「发帖 → 审核 → 发布」确认主流程未受影响。

## 3.4 删除用户面评论删除端点 · 决策：删（结论已修正）

> **口径**：用户不能自己删评论；即使违规也是由 AI 调接口删除。

上一版我建议「保留待前端接入」是**错的**——那基于「删评论是给用户用的」这个误判。按你的口径重新核查后，结论翻转为删除。理由是 AI 的删除能力**已经存在，而且比这个端点更完善**。

### AI 删违规评论的现有路径（已接通，保留）

不是 HTTP 端点，而是 MQ 审核回调链路：`ContentModerationTaskServiceImpl.deleteConfirmedViolation(targetType, targetId, contentHash)`。

两个调用方都是活的：

| 调用方 | 触发条件 |
|---|---|
| `ContentModerationTaskServiceImpl:129` | AI 审核结果 `finalStatus == "VIOLATION"` |
| `ContentReportServiceImpl:164` | 举报处理确认违规 |

它比用户面端点强的地方：

```200:223:java-cloud-standalone/content/server/src/main/java/org/pluchon/forum/service/impl/moderation/ContentModerationTaskServiceImpl.java
        if (TARGET_REPLY == safeByte(targetType)) {
            ArticleReply reply = articleReplyMapper.selectById(targetId);
            if (reply == null || DELETE_TRUE == safeByte(reply.getDeleteState())
                    || !contentHash.equals(sha256(reply.getContent()))) {
                return;
            }
```

- **contentHash 防篡改**：比对内容哈希，若用户在审核期间改了评论就不删（避免误删已修正的内容）。这正是我在 3.12 里提到的、auth 域没接上的那套机制，content 域是接了的
- **级联子评论**：先批量逻辑删除该评论下所有 `ArticleSubReply`，再删主评论
- **计数联动**：`handleDeletedReply` + `articleService.deleteReply` + 逐条 `deleteSubReply` 递减
- **三种目标类型**：弹幕 / 评论 / 子评论统一处理

### 要删除的部分

`ArticleReplyController.java:44` 的 `DELETE /articleReply/deleteReply`、`ArticleReplyService.deleteReply:16`（两参声明）、`ArticleReplyServiceImpl:204-232` 实现。

它与产品口径直接冲突——权限校验是「评论作者本人或帖子楼主」可删：

```215:218:java-cloud-standalone/content/server/src/main/java/org/pluchon/forum/service/impl/article/ArticleReplyServiceImpl.java
        Article article = articleService.selectArticleByArticleId(reply.getArticleId());
        if (!reply.getPostUserId().equals(loginUserId) && !article.getUserId().equals(loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
```

前端从未接入，所以线上不存在「用户自删评论」的行为；但端点是暴露的，属于与产品规则冲突的可达写入口，删掉同时是安全收益。

### 需要你确认的一点

现在 AI 删评论是**被动触发**（审核任务结果为 VIOLATION，或举报被确认）。如果你的设想是 AI 能**主动**发起删除（例如巡检历史评论），那需要新增一个内部契约（如 `ArticleReplyInternalApi` 的 `/articleReply/internal/delete-violation`），因为现有 `content/api` 的 4 个内部契约全是只读的，没有写入口。

我的建议是**先不新增**——被动链路已覆盖「违规即删」，主动巡检属新功能，等有需求再设计。

> **务必区分**：`ArticleService.deleteReply(Long articleId)`（单参，`ArticleServiceImpl:382`）是**另一个同名方法且是活的**，被 `ArticleReplyServiceImpl:231` 与 `ContentModerationTaskServiceImpl:219` 用来递减评论计数。删除两参方法时不要连坐——单参那个是 AI 路径也在用的。

## 3.5 删除回收站列表链 · 决策：删

> **口径**：帖子回收站不做。

删除范围：`ArticleController.java:234` 的 `GET /article/getDeletedArticleListWithPage`、`ArticleService.queryDeletedArticleListWithPage:45`、`ArticleServiceImpl:543`。前端从未接（连 API 封装都没写）。

逻辑删除基础设施（`delete_state`）本身保留，那是全局约定。

## 3.6 智能导读：澄清 + 删除 SSE 链 · 决策：删

> **你的疑问**：「智能导读我前端本来就有啊？」

**你看到的功能是真实存在的，但它不走 `streamGuide`。** 我上一版表述不清，澄清如下：

**是的，整条链路都不经过 `streamGuide`。** 这是两套并行实现，你在用的是摘要那套。逐段对照：

**你实际在用的链路（活的，全部保留）**

| 环节 | 位置 |
|---|---|
| 前端 UI | `ArticleDetail.vue:442-476`，完整的「AI 导读」卡片：`ai-guide-header` 标题、展开/收起按钮、重新生成按钮 |
| 前端逻辑 | `ArticleDetail.js:29` import `getArticleSummaryState`、`:32` import `regenerateArticleSummary` |
| 首次加载 | `ArticleDetail.js:1998` → `getArticleSummaryState()` → `GET /article/summary` |
| 重新生成 | `ArticleDetail.js:2067` → `regenerateArticleSummary()` → `POST /article/summary/regenerate`（timeout 65s） |
| 后端 | `ArticleSummaryServiceImpl`，注入 `ContentAiGatewayService`（第 78 行） |
| 到 AI | ai 域 AiHub → Python `POST_SUMMARY/GENERATE` intent |
| RAG | `ArticleSummaryServiceImpl.refreshRagIndex:277` 独立维护索引 |

**`streamGuide` 那套（死的，待删）**

| 环节 | 状态 |
|---|---|
| 后端 | `ArticleController.java:291` 的 SSE 端点 + `ArticleGuideStreamService` + `ArticleGuideStreamServiceImpl` 完整存在 |
| 前端封装 | `api/article.js:89-104` 的 `streamArticleGuide()`，手写 fetch + SSE 解析 |
| 前端调用 | **零**。全 `src/` 无任何文件 import 它；`ArticleDetail.js` 里搜 `guide` 标识符零命中（只有中文注释和 CSS 类名里有「导读」字样） |
| Python / Feign | 零（SSE 端点是给浏览器的，域间调用不会用） |

所以是「UI 标签叫导读、数据来自摘要接口」，而那条流式 SSE 是更早或并行的另一套方案，做完后端和 API 封装就没接上页面。删掉它对你看到的功能零影响。

顺带一个同源发现：`api/article.js:70` 还有个 `getAiSummary()` 指向 `GET /article/getSummary`，那是**旧摘要端点**，也无人 import——它和 `streamGuide` 属于同一批遗留（见 2.3 的旧摘要读取链）。真正在用的只有 `/article/summary` 与 `/article/summary/regenerate` 这两个。

删除范围：`ArticleController.java:291` 的 `GET /article/streamGuide`、`ArticleGuideStreamService` 接口、`ArticleGuideStreamServiceImpl`（含私有 `sendGuideSseChunk:98`、`streamGuideTextToClient:111`、`indexGuideSummaryToRag:117`）、前端孤儿封装 `api/article.js:89-104`。

**RAG 风险已评估为可接受**：`indexGuideSummaryToRag` 虽是 RAG 写入方之一，但它只被死代码调用，运行时早已不执行，所以删除**不会**改变任何现有写入行为。摘要侧的 RAG 索引由 `ArticleSummaryServiceImpl.refreshRagIndex:277` 与 `ArticleSearchIndexServiceImpl.indexPublishedArticle` 独立维护，不受影响。

## 3.7 删除三个上传端点 · 决策：删

> **口径**：管理端没做，可以删。

删除范围：`FileController.java:174 / :188 / :204`（`/file/uploadNoticePicture`、`/uploadLotteryPrizePicture`、`/uploadLotteryActivityPicture`）、`FileService.java:35 / :38 / :41`、`FileServiceImpl.java:299 / :310 / :321`、私有 `buildNoticePictureObjectName:342`，以及整个 `LotteryImagePathUtils` 工具类（它只为这三个端点服务）。

> 注意与 1.10 的区别：公告的**读取链保留**（前端 + Python 都在用），删的只是**管理端图片上传**。将来做管理端时重新添加。

## 3.8 下线逐项配额表 · 决策：删（本章最大杠杆）

> **口径**：前端只展示通用配额（只有 Qwen 一个模型）和生图配额，各模型逐项配额展示是很久以前的遗留。

**要保留的标量字段**（`MascotDock.js:231-235` 在读，会员中心也依赖）：`qwenBudgetMicros`、`qwenUsedMicros`、`qwenRemainingMicros`、`wanImageLimit`、`wanImageUsed`、`wanImageRemaining`。

**删除范围**（约 15 条一次性清掉）：

| 层 | 目标 |
|---|---|
| economy api | `VipQuotaPanelVO.groups` 字段、`VipQuotaGroupVO`、`VipQuotaItemVO`（含只写不读的 `scopeLabel` / `resetHint`）、`VipQuotaPanelVO.emptyHint` / `totalTokensUsed` / `activeBonusGrantCount` / `qwenBonusMicros` / `wanBonusCredits` |
| economy server | `VipCenterServiceImpl.toItem`、`isHiddenQuota`、`readDailyBucket`、`buildQuotaPanel` 中构建 groups 的第 346-365 行、`ForumVipQuotaConfig` 实体、`ForumVipQuotaConfigMapper` |
| ai api | `AiUsageDailyBucketsVO` 四个字段（`qwenFlashUsed` / `advancedLlmUsed` / `imageNormalUsed` / `companionNormalUsed`） |
| ai server | `AiUsageInternalService.usageSnapshot` 中读这四列的四行；`AiUsageDaily` 实体四个字段 |
| DB | `forum_vip_quota_config` 表（走 Flyway 前向迁移） |

**连带解决 1.5**（daily_count 恒显示 0 的矛盾口径随之消失），**并与 1.1 协同**：ai 域本地读 `ai_usage_daily` 的路径删掉后，`MascotController` 转发到 economy `/vip/internal/{userId}/quota-hint` 就是自然结果。

**`ai_usage_daily` 整表的后续问题**：删掉上述四列后，该表只剩 `cover_hint_used` 一列有写入（`incrementCoverHint`），而它**从来没有读取方**（`getCoverHintUsed` 全仓库零命中）。按「其他按建议来」，我建议**连整表一起下线**（`AiUsageDaily`、`AiUsageDailyMapper`、`AiQuotaServiceImpl.ensureRow`、`recordCoverHint`），因为纯审计写入却无任何报表消费它。若你希望保留生图提示的审计能力，则保留这一列并补一个查询出口。**这一条我会在动手前再确认一次。**

执行要点：改动跨 ai 与 economy 两域的 api 契约，必须两域同时编译验证；前端需回归会员中心与看板娘配额气泡。

## 3.9 保留看板娘长期记忆端点 · 决策：保留（功能待接通）

> **口径**：看板娘长期记忆是我们要做的功能，由 AI 模块自己调用，不需要前端。

保留：`AiWorkspaceController.java:88 / :94 / :101 / :110` 的 `memories` / `createMemory` / `setMemoryEnabled` / `deleteMemory` 及其 Service 实现。

**但必须指出一个链路缺口，否则这个功能不会生效**：

| 检查项 | 结果 |
|---|---|
| 前端调用 | 零（符合预期，你说了不需要前端） |
| **Python 调用** | **零**。`ai-server` 对 Java 只有 `/notice/center/list` 一个 HTTP 调用，不访问 `/ai/workspaces/**` |
| Java 内部调用 | 零。这套 CRUD 的唯一调用方就是它自己的 Controller |
| Prompt 组装侧是否读取 | **不读**。看板娘的记忆走的是 `ForumMascotMemory`（**另一张表**） |

也就是说「AI 模块自己调用」这件事**目前在代码里还不存在**——没有任何一侧在写入或读取这套数据，而看板娘实际用的是另一张表。

**需要你确认**：这套 `memories` 与看板娘现用的 `ForumMascotMemory` 是什么关系？两张表要合并，还是各管一段（比如 workspace 记忆 vs 对话记忆）？在接通之前，这套端点会一直处于「保留但零消费」状态。

## 3.10 AI 工作区端点部分删除 · 决策：按建议

删除：`AiWorkspaceController.java:40` 的 `create`、`:80` 的 `deleteWorkspace`（工作区由 `AiCompanionApiServiceImpl.ensureWorkspace` 隐式创建，显式创建/删除无人用）。

保留：`:47` 的 `list`、`:56` 的 `versions`（「作品版本历史」UI 的必要前置；前端已在用 `POST /ai/workspaces/{id}/versions` 与 `PUT .../selected-version/{versionId}`）。

## 3.11 删除看板娘模型列表端点 · 决策：删

> **口径**：不需要看板娘皮肤选择界面。

删除范围：`MascotController.java:80` 的 `GET /mascot/public/models`、`MascotService.listPublicModels` 及实现、`MascotModelPublicVO`。

保留：`ForumMascotModelMapper`（`setUserMascotPreference` 在用，是活的）。

## 3.12 删除三个无读写的字段与列 · 决策：按建议（删 Java 字段 + Flyway 前向迁移删列）

| 列 | 判断依据 |
|---|---|
| `user_profile_change_request.content_hash` | 其他域用同名字段做「审核期防篡改」，auth 的资料审核**没接这层**，改用「查是否存在更大 id 的记录」判定 superseded。属照抄模板的空壳 |
| `article.audit_notify_email` | 邻近审核字段全在用，只有「审核结果邮件通知」孤立无人问津，是没做完的通知功能残留 |
| `user_music.audio_fingerprint` | 四路搜索全零 |

执行要点：新增一个 Flyway 前向迁移，**禁止修改历史迁移文件**。删 `content_hash` 前确认该列若有唯一索引，幂等去重不依赖它（当前代码不依赖）。

## 3.13 content 域 VO 字段分类处理 · 决策：按建议

**保留并登记「前端待接」**（在 UI 上有明确展示价值）：

| 字段 | 价值 |
|---|---|
| `AuditStatusResponse.retryLimit` / `retryLimitReached` | 配套 `AuditRetryLimitGuard`，能告诉用户「审核重试次数已用完」 |
| `ContentReportVO.reportId` | 举报单号，用户凭它查处理进度 |
| `ArticleSummaryVO.retryAfterSeconds` | 摘要生成冷却提示 |

**删除**（纯技术性字段，无展示价值）：`CreatorDashboardVO.totalReadCount` / `weekEnd`、`CreatorInsightVO.generatedAt`、`ArticleSummaryVO.generatedAt`、`MusicParseResultVO.hasLyrics`、`ArticleValidateTextVO.isAllowed`（随 `validateText` 死链一起删）。

## 3.14 保留 im 域审计字段 · 决策：保留

`ChatMessageReport.reason`、`ImAiTask.triggerUserId`、`GroupChatJoinRequest.handledByUserId` 三个字段只写不读，但删了等于永久丢失审计线索。举报理由尤其重要——现在管理员**看不到用户为什么举报**，这是管理端接入时必须读出来的数据。

保留并登记为设计债。

## 3.15 `Constant` 转发门面 · 决策：单独立项，本次不动

257 个别名全部在用，但每个都只是 `ForumBusinessConstants` / `ForumRedisKeys` / `MqConstants` / `AuthConstants` / `OssPaths` 的别名，约 130 个文件通过 `Constant.` 访问，且已出现访问路径分裂（`HOT_RANK_*`、`SEARCH_KEYWORD_MAX_LEN` 被直接引用不走别名）。

属大范围重构，必须单独立项、单独 commit。按项目规则禁止用脚本批量替换。

## 3.16 保留 `ForumFeaturesProperties` · 决策：保留

五个字段被 `@EnableConfigurationProperties` 绑定但零 getter 调用（27 处开关走 `@ConditionalOnProperty` 直读原始键）。保留理由：它是唯一**集中声明**「五个开关默认全关」的地方，删掉后开关清单会散落在 27 个注解里。

---

# 第四章　误报与澄清记录

| 结论 | 说明 |
|---|---|
| **Python 会调用 Java，但调用面只有一个只读端点** | 最初的审查方法论只把前端当消费方，是漏洞。专项复核后确认：`ai-server` 唯一的 Java 客户端是 `forum_backend_client.py`，唯一方法 `list_published_notices()` → `/notice/center/list`。其余 `requests` 调用全部指向 DashScope / Tavily / ffmpeg / OSS；`mcp/` 下只有 datetime 与 tavily 工具。因此除公告端点外，其余「零调用」判定不受影响 |
| 前端「AI 导读」功能真实存在，但不走 `streamGuide` | UI 在 `ArticleDetail.vue:442`，数据来自 `/article/summary` 与 `/article/summary/regenerate`。`streamArticleGuide()` SSE 封装零 import，是另一套从未接通的实现。详见 3.6 |
| 删评论既不是 Python 调用、也不该给用户用 | 我曾建议「保留待前端接入」，那是基于误判。AI 删违规评论走 MQ 审核回调 `ContentModerationTaskServiceImpl.deleteConfirmedViolation`（含 contentHash 防篡改 + 级联子评论），已接通且更完善；用户面的 `DELETE /articleReply/deleteReply` 与「用户不能自删」的口径冲突，应删除。详见 3.4 |
| AI 访问 Java 业务走域间 Feign，且现有内部契约全是只读 | `content/api` 只有 `ArticleInternalApi`（批量查帖/搜索候选/点赞标题/收藏歌曲标题）、`FileInternalApi`（AI 生图转存 OSS）、`UserEngagementInternalApi`、`FavoriteFolderInternalApi`。在全部 `*Feign*.java` 中搜索本次 15 个待删端点标识符，零命中 |
| `ContentForumConsumer` 重复 `@ConditionalOnProperty` **不是编译错误** | 已实测 `mvn -pl content/server -am compile` **退出码 0**。该注解在本项目 Spring Boot 版本中可重复，两个条件是「与」关系，语义正确。另经 `git show 5db3f89b` 确认，重复标注在上次提交前就存在 |
| `match_rejected` **不是**前端死分支 | 实际代码是 `message?.type === 'match_failed' \|\| message?.type === 'match_rejected'`，`match_failed` 后端确实会发 |
| 首页顶部 PRO 提示的数据源是**正确**的 | `useVipStatusEntry.js` 调 economy `/vip/status` 读权威表，不依赖 auth 快照，且会回写 `userStore`。故 1.3 下线头像环不波及它 |
| `AuthSnapshotResolver` 无法自动装配是 IDEA 假阳性 | 微服务架构下每个服务运行时 classpath 只有一个实现 |
| 零 getter 不等于死字段 | 以下字段绕过实体、由原生 SQL 或 Lambda `.set(Entity::getXxx, ...)` 读写，**误删会导致故障**：`ForumAiQuotaPeriodUsage.wanUsedCount` / `wanReservedCount` / `quotaPeriodKey`（配额预占）、`ForumRedisKeys.EMPTY_MARK` / `USER_LIKES` / `TTL_USER_LIKES`（点赞缓存与空值防穿透）、`ForumOutboxMessage.eventId`（幂等性物理保障）、`CreatorDailyMetric.publishCount`（`INSERT ... ON DUPLICATE KEY UPDATE` 原子写）、`Article.auditTaskId` 等审核字段、`ForumArticleAiFeature.summaryGeneratedAt`、`UserMusic.aiAnalyzedAt` |
| 单向契约字段不是残留 | `AiGobangBoardInsight.stones` / `myThreats` / `oppThreats` 由 game 域 set 后序列化交 Python 消费，Java 侧零 getter 属正常 |
| Python 侧 `CONTENT_MODERATION/ARTICLE_AUDIT` 注册无 Java 调用方 | 帖子审核走 RabbitMQ 异步链路（`workers/audit_worker.py` 直接 `from graphs.article_audit import run_audit`）绕开 gateway。Java 侧 26 个 intent 组合全部对齐 |
| `ForumDateTimes.ZONE_SHANGHAI` 命名与取值不一致 | 常量名是 SHANGHAI，值是 `ZoneId.of("Asia/Taipei")`。偏移量相同故行为无误，建议核对是否有意为之 |
| auth 域 Mapper 层已清理干净 | 四个 Mapper 现均为空的 `extends BaseMapper<T>`，业务侧全走 Lambda API |
| gateway 模块零残留 | 3 个文件全部存活。`InternalPathBlockGlobalFilter` 与 common 的 `InternalApiConstants.INTERNAL_PATH_MARKER` 各自硬编码 `/internal/`，属合理物理隔离，但需知晓这是两处要同步修改的地方 |

---

# 第五章　方法论与已知局限

**验证手段**：对每个候选项在 `java-cloud-standalone/`、`forum-vue/front/src/`、`ai-server/` 三处做限定形式搜索（`类名.成员名`、`.方法名(`、`::方法名`），区分「零引用」「仅本文件引用」「仅被框架调用」。字段判定要求四路同时为零：字段名、Lombok 推导的 getter/setter、前端 camelCase JSON key、Python 侧 key。端点判定除搜路径片段外，还验证前端 `src/api/*.js` 的封装函数本身是否被任何页面 import（已确认前端无 `import * as xxxApi` 命名空间导入），并已补充 Python 调用面的专项复核（见开头）。

已识别为框架存活并排除：`@Bean`、`@ExceptionHandler`、`@Override`、`@Value` setter、`@PostConstruct`、`@Scheduled`、`@RabbitListener`、`GlobalFilter`、`HandlerInterceptor` 实现、通过 `@Qualifier` 注入的 Executor Bean。

**静态搜索无法覆盖的三类引用**，删除前建议用 IDEA 的 Find Usages 逐条复核：

1. 反射按字符串名调用
2. Spring SpEL 表达式、AOP 切点表达式中的引用
3. Nacos 远端配置中的键名 / 类名字符串（仓库内 `deploy/nacos-config/` 与各模块 `application*.yml` 已检查，**线上 Nacos 实际配置未核对**）

**Postman collection 已删除**，因此原先「某端点只在 Postman 中有记录」这条证据来源不再存在。若将来需要接口清单，依赖 springdoc/Swagger。

**未做的验证**：除一次 content 模块编译外，未做整体编译、未跑迁移、未在浏览器验证。
