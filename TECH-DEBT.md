# 技术债登记

2026-08-28 残留代码专项清理（P1–P4）完成后，从审计文档中摘出的未落地项。每条都是**当时刻意没改**的，不是漏掉的。

## 1. 支付接通当天必须补 `vip_purchase_record` 写入 · 有资金风险

`VipCenterServiceImpl` 判定首购资格靠 `selectCount(...) == 0`，而 `vip_purchase_record` 表**当前零写入方**（`VipPurchaseRecordMapper` 是空的 `BaseMapper`），所以 `firstPurchaseEligible` 恒为 true。

mock 期间「所有人都能看到首月优惠价 3.9 / 6.9」是预期行为。但**支付渠道接通当天必须同步补上购买记录的 insert**，否则首月优惠价会被同一用户无限次复用。

保留清单（支付接通后的写入落点，勿删）：`VipPurchaseRecord` 实体、`VipPurchaseRecordMapper`、`VipPurchaseRecordConverter`、`VipCenterServiceImpl.purchaseRecords`、`/vip/purchase-records` 端点、`UserVipSubscriptionMapper.updatePaidSubscription`、`VipSubscribeResultVO`、`VipSubscribeDTO`。

## 2. VIP 礼包额度未独立结算

`vip_quota_bonus_grant` 有 14 条记录，但 `qwen_used_micros` / `wan_used_credits` 与两个 reserved 列**全为 0**——礼包额度从未被消费过。

修好等于实现一套「礼包额度独立于基础额度结算」的机制，属新增功能。`buildQuotaPanel` 里的恒等变换与 5 个礼包契约类**一并保留**，作为将来接通的落点。

## 3. 群聊表情收藏丢来源

`FavoriteEmojiRequest.originGroupMessageId` 全仓库只有两处：DTO 声明与前端 `stores/chatEmoji.js` 赋值。后端 `MessageServiceImpl.favoriteEmoji` 只处理 `originMessageId`，`user_chat_emoji` 表也只有 `origin_message_id` 一列。

修好需要给 `user_chat_emoji` 增列存群聊来源，属 schema 扩展。

> **注意**：DTO 字段与前端赋值是**故意保留**的，为了让这个缺口保持可见。它们看起来像残留字段，但删掉会让「群聊表情收藏丢来源」这个缺陷彻底失去线索。

## 4. 两个 AI 音乐功能未纳入计费白名单 · 当前口径为免费

`AiHubServiceImpl` 中 `music_recommend` 与 `music_ai_search` 走 `billBatch`，但 `BillableFeature` 白名单只有 `ARTICLE_TAG_RECOMMEND`，`contains` 返回 false，所以这两个功能实际免费，`billable_state` 落库为 0。

**现状即为生效口径**，代码无需改动。将来若要收费，把这两个 feature 加入白名单即可，计费链路本身是活的。

## 5. content 域三个字段「前端待接」

后端已产出、前端未展示，均有明确 UI 价值：

| 字段 | 价值 |
|---|---|
| `AuditStatusResponse.retryLimit` / `retryLimitReached` | 配套 `AuditRetryLimitGuard`，能告诉用户「审核重试次数已用完」 |
| `ContentReportVO.reportId` | 举报单号，用户凭它查处理进度 |
| `ArticleSummaryVO.retryAfterSeconds` | 摘要生成冷却提示 |

## 6. im 域三个只写不读的审计字段 · 设计债

`ChatMessageReport.reason`、`ImAiTask.triggerUserId`、`GroupChatJoinRequest.handledByUserId` 只写不读，但删了等于永久丢失审计线索。

举报理由尤其重要——管理员现在**看不到用户为什么举报**，这是管理端接入时必须读出来的数据。

## 7. `Constant` 转发门面需单独立项重构

257 个别名全部在用，但每个都只是 `ForumBusinessConstants` / `ForumRedisKeys` / `MqConstants` / `AuthConstants` / `OssPaths` 的别名，约 130 个文件通过 `Constant.` 访问，且已出现访问路径分裂（`HOT_RANK_*`、`SEARCH_KEYWORD_MAX_LEN` 被直接引用不走别名）。

属大范围重构，必须单独立项、单独 commit，且按项目规则禁止用脚本批量替换。

---

## 附：审计方法论教训（供下次专项参考）

本次审计有 4 项「功能失效」判定是误报（1.1 看板娘积分付费入口、1.7 WebSocket 错误提示、1.8 收件人校验 Guard，以及被误判为死字段的 `GobangRoomParticipantVO.joinedAtMs`），成因高度一致：

- **按标识符/类型名 grep 前端，零命中就判定「未接通」**，没有读通用兜底分支。例如三个游戏房间的 `onMessage` 开头统一有 `if (!message.ok) ElMessage.warning(message.message)`，按 `ok` 标志而非类型名分派。
- **只看返回值是否被使用，忽略副作用链路**。例如 `ReceiverExistsGuard` 丢弃返回值，但下游 Feign 会抛异常，校验实际生效。
- **只 grep setter 判断字段有无写入方**，忽略 `@AllArgsConstructor` 全参构造赋值（`EmojiShopDetailVO` 两个会员字段即如此）。

结论若为「前端零处理」或「字段零写入」，必须再确认一次是否存在统一处理分支、副作用链路或构造器赋值。
