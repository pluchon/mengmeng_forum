# 技术债与待办登记

2026-08-28 残留代码专项清理（P1–P4）完成后的遗留项。已按产品口径逐条定性。

---

## 一、待实现功能

### 1. 举报跟进入口 · 决定要做（界面待设计）

`ContentReportVO.reportId` 已由后端返回但前端未接。口径：在**消息中心的通知模块**加一个「举报跟进」分类，图标用警报样式，用户凭举报单号查处理进度。

本次只定方向，具体界面另行设计。

### 2. 审核重试次数用完的提示 · 决定要做

`AuditStatusResponse.retryLimit` / `retryLimitReached` 配套 `AuditRetryLimitGuard`，后端已产出、前端未展示。用户重试到上限时应当被明确告知，不能静默失败。

---

## 二、需讨论

### 4. VIP 礼包额度未独立结算

`vip_quota_bonus_grant` 有 14 条记录，但 `qwen_used_micros` / `wan_used_credits` 与两个 reserved 列**全为 0**——礼包额度从未被消费过。

真正修好等于实现一套「礼包额度独立于基础额度结算」的机制。`buildQuotaPanel` 里的恒等变换与 5 个礼包契约类**一并保留**，作为将来接通的落点。

**状态：列入讨论项，方案未定。**

---

## 三、必须留档的风险

### 5. 支付接通当天必须补 `vip_purchase_record` 写入

`VipCenterServiceImpl` 判定首购资格靠 `selectCount(...) == 0`，而 `vip_purchase_record` 表**当前零写入方**（`VipPurchaseRecordMapper` 是空的 `BaseMapper`），所以 `firstPurchaseEligible` 恒为 true。

mock 期间「所有人都能看到首月优惠价 3.9 / 6.9」是预期行为。但**支付渠道接通当天必须同步补上购买记录的 insert**，否则首月优惠价会被同一用户无限次复用。

保留清单（支付接通后的写入落点，勿删）：`VipPurchaseRecord` 实体、`VipPurchaseRecordMapper`、`VipPurchaseRecordConverter`、`VipCenterServiceImpl.purchaseRecords`、`/vip/purchase-records` 端点、`UserVipSubscriptionMapper.updatePaidSubscription`、`VipSubscribeResultVO`、`VipSubscribeDTO`。

---

## 四、已定性为产品设计，非技术债

### 6. AI 音乐功能全部免费

`AiHubServiceImpl` 中 `music_recommend` 与 `music_ai_search` 走 `billBatch`，但 `BillableFeature` 白名单只有 `ARTICLE_TAG_RECOMMEND`，`contains` 返回 false，所以这两个功能不扣费，`billable_state` 落库为 0。

**这不是漏配。** 免费解析、免费推荐是产品特色，音乐相关的 AI 能力一律免费。当前代码行为即为正确口径，**不要**「修复」成收费。

### 7. 摘要生成冷却不对外提示

摘要由后端静默生成，冷却期内前端继续展示旧结果，无需向用户暴露冷却剩余秒数。

已据此删除 `ArticleSummaryVO.retryAfterSeconds`（本次落地）。冷却本身仍然生效——`ArticleSummaryServiceImpl` 内部保留 `retryAfter` 计算，用于 `canRegenerate` 控制重新生成按钮的可用性。

### 8. im 域三个只写不读的审计字段 · 决定保留

`ChatMessageReport.reason`、`ImAiTask.triggerUserId`、`GroupChatJoinRequest.handledByUserId` 目前只写不读，管理端未做，但**一律保留**。

- `reason`：用户举报时被 `@NotBlank @Size(min = 5, max = 200)` 强制填写 5–200 字。只删存储会让用户的输入被静默丢弃，比现状更糟；数据留在库里，管理端一接就能读。
- `triggerUserId`（谁触发了审核任务）、`handledByUserId`（谁批准了入群申请）：后端自动写入，各一个 `bigint`，保留成本几乎为零，出纠纷时可查责任人。

> 这三个字段**不是残留**，下次审计不要按「零读取」删掉。

### 9. `Constant` 转发门面不重构

257 个别名全部在用，每个都只是 `ForumBusinessConstants` / `ForumRedisKeys` / `MqConstants` / `AuthConstants` / `OssPaths` 的别名，约 130 个文件通过 `Constant.` 访问。

**决定不动。** 收益不足以匹配牵一发动全身的风险。

---

---

## 五、本轮已落地

### 群聊表情收藏来源（原第 1 条待实现项）

`user_chat_emoji` 增列 `origin_group_message_id`，`favoriteEmoji` 落库群聊来源，收藏列表仍是**一份合并列表**，前端不区分来源。

顺带修掉一个此前没被识别的真实缺陷：`queryEmojiList` 与前端 `utils/chatMedia.js` 都只按 `originMessageId` 区分「我的上传 / 收藏」，而群聊收藏的该字段为空。若收藏的图片 URL 落在 `emoji/` 目录（对方从自己表情库发出的表情），会被误归入「**我的上传**」。两侧分类逻辑均已改为「私信或群聊来源任一非空即为收藏」。

历史数据靠 URL 目录反推的兼容分支保留（现网 13 条收藏中 1 条有私信来源、0 条有群聊来源）。

## 附：审计方法论教训

本次审计有 4 项「功能失效」判定是误报（看板娘积分付费入口、游戏 WebSocket 错误提示、私信收件人校验 Guard，以及被误判为死字段的 `GobangRoomParticipantVO.joinedAtMs`），成因高度一致：

- **按标识符/类型名 grep 前端，零命中就判定「未接通」**，没有读通用兜底分支。例如三个游戏房间的 `onMessage` 开头统一有 `if (!message.ok) ElMessage.warning(message.message)`，按 `ok` 标志而非类型名分派。
- **只看返回值是否被使用，忽略副作用链路**。例如 `ReceiverExistsGuard` 丢弃返回值，但下游 Feign 会抛异常，校验实际生效。
- **只 grep setter 判断字段有无写入方**，忽略 `@AllArgsConstructor` 全参构造赋值（`EmojiShopDetailVO` 两个会员字段即如此）。
- **grep 大小写不匹配导致漏判**。字段名 `triggerUserId` 匹配不到 setter 调用 `setTriggerUserId`。

结论若为「前端零处理」或「字段零写入」，必须再确认一次是否存在统一处理分支、副作用链路、构造器赋值，以及 grep 模式本身的大小写覆盖。
