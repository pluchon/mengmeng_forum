# 技术债与待办登记

2026-08-28 残留代码专项清理（P1–P4）完成后的遗留项。仅登记**待实现**与**需讨论**两类。

---

## 一、待实现

### 1. 举报理由没有传给 AI 审核

举报弹窗（`ReportReasonDialog`）给了 6 个预设选项——辱骂攻击、色情低俗、违法违规、垃圾广告、虚假误导、其他理由，只有选「其他」才要求手写（≥5 字）。提交的是选项文案本身。

**但这个理由从未到达 AI。** 两个域的任务载荷都不含它：

- `ChatMessageReportServiceImpl.taskPayload` 只有 `taskId` / `taskType` / `targetType` / `targetId` / `contentHash` / `content` / `resultDomain`
- `ContentReportServiceImpl.taskPayload` 只多一个 `title`
- Python 侧 `ai_async_worker._execute` 构造 `ModuleRequest` 时 `payload={"title", "content"}`，也没有接收位

结果是 AI 在**不知道用户举报的是什么问题**的情况下孤立判定内容。举报「垃圾广告引流」这类需要意图判断的场景最吃亏——单看文本像正常聊天，带上举报分类才容易判出来。

要做的话涉及三处：两个域的 `taskPayload` 增加 `reportReason`、Python 侧透传进 payload、审核 prompt 里作为「举报人主张」呈现（须明确只是线索、不是结论，避免被恶意举报带偏）。

**一个设计要先定：任务是多人共享的。** `findSharedTask` 按「消息 + contentHash」把多人对同一条内容的举报合并成一个 AI 任务，所以可能存在多个不同理由。要么取创建任务那条举报的理由，要么聚合去重后全部带上。

### 2. 举报跟进入口（界面待设计）

`ContentReportVO.reportId` 已由后端返回但前端未接。口径：在**消息中心的通知模块**加一个「举报跟进」分类，图标用警报样式，用户凭举报单号查处理进度。

本次只定方向，具体界面另行设计。

---

## 二、需讨论

### 3. VIP 礼包的额外额度显示得到、实际用不到

先把**现有规则**记下来，这套逻辑是完整的、按预期工作的（`VipSubscribeServiceImpl.grantTrialVipDays` + `VipEntitlementServiceImpl`）：

| 领取前状态 | 实际发放档位 | 时长折算 | 到期时间 |
| --- | --- | --- | --- |
| 非会员 / 已过期 | PRO | N 天 × 24h | 从当下起算 |
| PRO 生效中 | PRO | N 天 × 24h | 在原到期时间上叠加 |
| MAX 生效中 | 保持 MAX | N 天 × **12h**（折半） | 在原到期时间上叠加 |

MAX 用户拿 PRO 体验卡按 2:1 折算成 MAX 时长，不会被降级；`resolveGrantTier` 另有一道保护，高档位生效中时不会被低档位礼包顶掉。来源（星辉商城 / 连续签到 / 抽奖池 / 收集册里程）各自传 `sourceType` 与幂等键，不会重复发放。

**问题出在礼包附带的那份额外额度上。** 除了档位提升，`grantTrialBonus` 还按 PRO 月额度线性折算发一份独立额度（7 天档 ≈ 254 万 micros 文本 + 4.67 张图，30 天有效期）。它：

- **面板认**：`buildQuotaPanel` 把 `qwenGrantedMicros` 加进显示上限，用户在会员中心看得到
- **门槛不认**：真正拦调用的 `AiQuotaServiceImpl.ensureTextBudget` / `consumeImageNormal` 只按 VIP 档位取上限（`isMax ? 20_900_000 : isProOrMax ? 10_900_000 : 6_000_000`），完全不查礼包表

所以基础额度一见底就被 `FAILED_AI_QUOTA_EXCEEDED` 拦死，**尽管面板还显示有剩余**。用户花星辉换来的那部分额外额度是虚的。

配套的三阶段结算 API（`reserveBonus` / `settleBonus` / `releaseBonus`）在 `VipInternalApi`、`VipInternalController`、`VipQuotaBonusServiceImpl` 三处都齐全，但**全仓无任何调用方**；`vip_quota_bonus_grant` 的 `qwen_used_micros` / `wan_used_credits` 及两个 reserved 列因此恒为 0。

需要定的是：礼包额度到底算「独立可叠加的额外额度」（那要把 AI 域的门槛改成查礼包、并接上三阶段结算），还是「只是档位体验卡」（那就把面板里加 bonus 的显示去掉，别让用户看到用不到的数字）。前者是完整实现，后者是几行显示修正。

**方案未定。** 相关契约类与 `buildQuotaPanel` 里的 base/bonus 拆分逻辑一并保留，作为将来接通的落点。

### 4. 支付接通当天必须补 `vip_purchase_record` 写入

`VipCenterServiceImpl` 判定首购资格靠 `selectCount(...) == 0`，而 `vip_purchase_record` 表**当前零写入方**（`VipPurchaseRecordMapper` 是空的 `BaseMapper`），所以 `firstPurchaseEligible` 恒为 true。

mock 期间「所有人都能看到首月优惠价 3.9 / 6.9」是预期行为。但**支付渠道接通当天必须同步补上购买记录的 insert**，否则首月优惠价会被同一用户无限次复用。

保留清单（支付接通后的写入落点，勿删）：`VipPurchaseRecord` 实体、`VipPurchaseRecordMapper`、`VipPurchaseRecordConverter`、`VipCenterServiceImpl.purchaseRecords`、`/vip/purchase-records` 端点、`UserVipSubscriptionMapper.updatePaidSubscription`、`VipSubscribeResultVO`、`VipSubscribeDTO`。

---

## 附：审计方法论教训

本次审计有 4 项「功能失效」判定是误报（看板娘积分付费入口、游戏 WebSocket 错误提示、私信收件人校验 Guard，以及被误判为死字段的 `GobangRoomParticipantVO.joinedAtMs`），成因高度一致：

- **按标识符/类型名 grep 前端，零命中就判定「未接通」**，没有读通用兜底分支。例如三个游戏房间的 `onMessage` 开头统一有 `if (!message.ok) ElMessage.warning(message.message)`，按 `ok` 标志而非类型名分派。
- **只看返回值是否被使用，忽略副作用链路**。例如 `ReceiverExistsGuard` 丢弃返回值，但下游 Feign 会抛异常，校验实际生效。
- **只 grep setter 判断字段有无写入方**，忽略 `@AllArgsConstructor` 全参构造赋值（`EmojiShopDetailVO` 两个会员字段即如此）。
- **grep 大小写不匹配导致漏判**。字段名 `triggerUserId` 匹配不到 setter 调用 `setTriggerUserId`。

结论若为「前端零处理」或「字段零写入」，必须再确认一次是否存在统一处理分支、副作用链路、构造器赋值，以及 grep 模式本身的大小写覆盖。

> 另注：`ChatMessageReport.reason`、`ImAiTask.triggerUserId`、`GroupChatJoinRequest.handledByUserId` 三个字段目前只写不读，已确认**一律保留**，下次审计不要按「零读取」删掉。
