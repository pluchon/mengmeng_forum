# 技术债与待办登记

2026-08-28 残留代码专项清理（P1–P4）完成后的遗留项。仅登记**待实现**与**需讨论**两类。

---

## 一、待实现

### 1. 举报跟进入口（界面待设计）

`ContentReportVO.reportId` 已由后端返回但前端未接。口径：在**消息中心的通知模块**加一个「举报跟进」分类，图标用警报样式，用户凭举报单号查处理进度。

本次只定方向，具体界面另行设计。

---

## 二、需讨论

### 2. 充值开通链路整条未实现

**当前会员的唯一实际来源是抽奖池**：常规池 PRO 30 天（权重 20，限量 30），神秘大奖池 MAX 30 天（神秘大奖档内再取 1/1001）。

充值不是「已实现但走 mock 支付」，而是**从接口到落库整条链路都还没写**：`VipController` 只有 center / quota / purchase-records / status 四个 `@GetMapping`，没有任何下单或开通的写接口；`VipSubscribeDTO`、`VipSubscribeResultVO`、`UserVipSubscriptionMapper.updatePaidSubscription` 三者全仓零调用方。前端会员中心的价格是纯展示。

接通支付时要一并补齐三件事：

1. **开通写接口**，落库走 `updatePaidSubscription`（注意它会重写 `quota_period_*`，锚点语义见已解决项第 3 条）
2. **`vip_purchase_record` 的 insert**。`VipCenterServiceImpl` 判定首购资格靠 `selectCount(...) == 0`，该表当前零写入方，所以 `firstPurchaseEligible` 恒为 true——不补上，首月优惠价 3.9 / 6.9 会被同一用户无限次复用
3. **显式传档位**。`grantTrialVipDays`（默认 PRO 的便捷重载）已删除，`grantTrialVip` 强制要求指定 tier，避免 MAX 被静默降级

保留清单（支付接通后的落点，勿删）：`VipPurchaseRecord` 实体、`VipPurchaseRecordMapper`、`VipPurchaseRecordConverter`、`VipCenterServiceImpl.purchaseRecords`、`/vip/purchase-records` 端点、`UserVipSubscriptionMapper.updatePaidSubscription`、`VipSubscribeResultVO`、`VipSubscribeDTO`。

> 档位保护规则（不是新来源，但影响到手档位）：MAX 生效中的用户领 PRO 卡不会降级，而是按 12 小时/天折半延长 MAX。即 MAX 用户抽到 PRO 30 天卡，实际到手 15 天 MAX。

---

## 附：已解决项备忘

以下项曾登记为技术债，现已实现，仅留结论备查：

- **举报理由接进 AI 审核**：两个域的 `taskPayload` 增加 `reportReason`，多人共享任务时按 `taskId` 聚合去重（上限若干条，`；` 拼接）；Python 侧 `ai_async_worker` 透传，`moderation/graph.py` 的 prompt 以 `<untrusted_report_reason>` 呈现并明确「仅提示审核视角、本身不是证据、不得因此降低判定标准」。带理由的举报**整体绕过语义缓存**——缓存按内容取键不含理由，复用会让理由白传，写回则会污染普通自动审核。
- **VIP 礼包额外额度显示得到用不到**：不再做独立额度池结算，改为**额度重置卡**（星辉商城 600 星辉，PRO/MAX 同价、重置效果随档位不同）。`vip_quota_bonus_grant` 整套表/服务/跨域契约已下线。
- **AI 周期键随续期漂移**：周期锚点改取 economy 域 `user_vip_subscription.quota_period_*`，不再从 `vip_expire_at` 反推，续期不再等于白送一次额度重置。
- **会员体验卡发放来源统一**：体验卡**只从抽奖池发放**——常规池 PRO 30 天（权重 20、限量 30），神秘大奖池 MAX 30 天（权重 1）。其余四个来源全部下线：连续签到 15/30 天档退回纯星辉（`MIXED`→`STARLIGHT`，`vip_days` 列已删）、签到惊喜奖池移除 VIP 档位（类间权重 1000→900，其余四类相对概率不变）、星辉商城改卖额度重置卡、收集册 80 档改为抵扣券×5。相应地 `CheckinServiceImpl` / `StarlightShopServiceImpl` / `LotteryServiceImpl` 的体验卡发放分支与 `LOTTERY_COLLECT_REWARD_VIP_DAYS` 常量一并移除，规则由代码强制而非仅靠数据。

---

## 附：审计与操作方法论教训

本次审计有 4 项「功能失效」判定是误报（看板娘积分付费入口、游戏 WebSocket 错误提示、私信收件人校验 Guard，以及被误判为死字段的 `GobangRoomParticipantVO.joinedAtMs`），成因高度一致：

- **按标识符/类型名 grep 前端，零命中就判定「未接通」**，没有读通用兜底分支。例如三个游戏房间的 `onMessage` 开头统一有 `if (!message.ok) ElMessage.warning(message.message)`，按 `ok` 标志而非类型名分派。
- **只看返回值是否被使用，忽略副作用链路**。例如 `ReceiverExistsGuard` 丢弃返回值，但下游 Feign 会抛异常，校验实际生效。
- **只 grep setter 判断字段有无写入方**，忽略 `@AllArgsConstructor` 全参构造赋值（`EmojiShopDetailVO` 两个会员字段即如此）。
- **grep 大小写不匹配导致漏判**。字段名 `triggerUserId` 匹配不到 setter 调用 `setTriggerUserId`。

结论若为「前端零处理」或「字段零写入」，必须再确认一次是否存在统一处理分支、副作用链路、构造器赋值，以及 grep 模式本身的大小写覆盖。

> 另注：`ChatMessageReport.reason`、`ImAiTask.triggerUserId`、`GroupChatJoinRequest.handledByUserId` 三个字段目前只写不读，已确认**一律保留**，下次审计不要按「零读取」删掉。

### MySQL 命令行导入必须显式声明字符集

开发容器的 `character_set_client` 默认是 **latin1**。`create.sql` 里 mysqldump 风格的 `SET character_set_client = utf8mb4` 只在每个 `CREATE TABLE` 前后成对生效，**到后面的 seed INSERT 时已还原成客户端默认值**，导致所有中文初始化数据按 latin1 写入变成乱码（本次 `AI额度重置卡`、`PRO会员体验卡·30天` 两行即如此，已修正）。

已在六个域的 `create.sql` 开头统一加 `SET NAMES utf8mb4;`，脚本自身即可免疫客户端默认值。另外，手动执行 `docker exec ... mysql` 写入中文时必须带 `--default-character-set=utf8mb4`，否则同样会写坏。应用侧不受影响：JDBC 连接串已带 `characterEncoding=utf8`。
