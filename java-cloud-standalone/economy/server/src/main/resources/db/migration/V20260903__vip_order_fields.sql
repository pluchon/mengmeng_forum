-- 会员订单状态流转所需字段（前向迁移，只跑一次）
-- 背景：
--   vip_purchase_record 建表时只考虑了"支付成功后记一笔流水"，
--   但真正要做的是订单：下单先落待支付、回调再发货。缺的这几列是流程必需，
--   而不是锦上添花：
--   payment_channel / channel_trade_no —— 对账时必须能定位到渠道那一侧的单据；
--   price_plan  —— 升级差价要用"当初买 PRO 所用的那套定价体系"，
--                  没有它就无法区分 3 元差额与 6 元差额；
--   order_kind  —— 新购 / 续费 / 升级三种发货规则完全不同；
--   expected_expire_at —— 下单时锁定的会员到期日。发货前拿它和当前值比对，
--                  不一致就拒绝发货。这是"挂单不付、等续费后再付"白嫖手法的唯一拦截点；
--   paid_at / closed_at —— 终态时间，和 create_time 分开才能算支付时长与超时。

ALTER TABLE `vip_purchase_record`
  ADD COLUMN `payment_channel` varchar(32) NOT NULL DEFAULT 'mock' COMMENT '支付渠道: mock/alipay/wechat' AFTER `payment_order_no`,
  ADD COLUMN `channel_trade_no` varchar(128) DEFAULT NULL COMMENT '渠道流水号，对账用' AFTER `payment_channel`,
  ADD COLUMN `price_plan` varchar(24) NOT NULL DEFAULT 'normal' COMMENT '定价体系: first_purchase/normal' AFTER `channel_trade_no`,
  ADD COLUMN `order_kind` varchar(16) NOT NULL DEFAULT 'new' COMMENT '订单类型: new/renew/upgrade' AFTER `price_plan`,
  ADD COLUMN `expected_expire_at` datetime DEFAULT NULL COMMENT '下单时锁定的会员到期日，发货前校验' AFTER `order_kind`,
  ADD COLUMN `paid_at` datetime DEFAULT NULL COMMENT '支付成功时间' AFTER `period_end`,
  ADD COLUMN `closed_at` datetime DEFAULT NULL COMMENT '订单关闭时间' AFTER `paid_at`;

-- 待支付订单的清扫与"同一用户只留一单"都按这个组合查
ALTER TABLE `vip_purchase_record`
  ADD KEY `idx_vip_purchase_pending` (`payment_state`,`create_time`);

-- 历史流水（如果有）一律视为已支付的新购单，补上终态时间
UPDATE `vip_purchase_record`
SET `paid_at` = `create_time`
WHERE `payment_state` = 1 AND `paid_at` IS NULL;
