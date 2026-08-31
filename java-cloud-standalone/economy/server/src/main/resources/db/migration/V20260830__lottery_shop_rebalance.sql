-- 抽奖与萌星辉商城的经济性调整（前向迁移，可重复执行）
-- 背景：
--   1. 神秘大奖档位 stock_remaining 是 1，全站第一个抽到的人拿走后档位永久消失，
--      硬保底会一直落到"回退全池"，等于保底失效。改为无限：它只是个盲盒容器，
--      稀缺性下沉到子奖项承担。
--   2. 子奖项此前只有 100/80 积分与一个权重 1 的 MAX 会员，期望值 ≈ 90 积分，
--      而单抽成本 30 —— "大奖"只比普通积分奖好 3 倍，名不副实。
--   3. 抵扣券与补签卡是可无限生成的虚拟货币，限量没有意义，售罄反而变相涨价；
--      真正有成本的 AI 额度重置卡却没有任何总量约束。

-- 子奖项支持限量：-1 无限，>=0 限量
ALTER TABLE `lottery_prize_mystery_item`
  ADD COLUMN `stock_remaining` int NOT NULL DEFAULT -1 COMMENT '剩余库存 -1无限' AFTER `weight`;

-- 商城支持周限购
ALTER TABLE `starlight_shop_item`
  ADD COLUMN `weekly_limit` int NOT NULL DEFAULT 0 COMMENT '每周限购 0不限' AFTER `daily_limit`;

-- 神秘大奖档位转为无限库存
UPDATE `lottery_activity_prize` SET `stock_remaining` = -1 WHERE `id` = 2;

-- 子奖项重配：500积分 70% / 700积分 25% / MAX会员30天 5%，会员限量 20 份
UPDATE `lottery_prize_mystery_item` SET `item_value` = 500, `weight` = 700, `stock_remaining` = -1 WHERE `id` = 2;
UPDATE `lottery_prize_mystery_item` SET `item_value` = 700, `weight` = 250, `stock_remaining` = -1 WHERE `id` = 3;
UPDATE `lottery_prize_mystery_item` SET `item_value` = 30, `weight` = 50, `stock_remaining` = 20 WHERE `id` = 4;

-- 收集册四档改为确定奖励并拉开梯度（原 10 档是 RANDOM，五五开给券或积分）
UPDATE `lottery_collect_milestone` SET `reward_type` = 'VOUCHER', `reward_value` = 3,   `alt_reward_value` = NULL, `label` = '抵扣券×3'  WHERE `threshold_count` = 10;
UPDATE `lottery_collect_milestone` SET `reward_type` = 'POINTS',  `reward_value` = 200, `alt_reward_value` = NULL, `label` = '积分×200'  WHERE `threshold_count` = 25;
UPDATE `lottery_collect_milestone` SET `reward_type` = 'VOUCHER', `reward_value` = 10,  `alt_reward_value` = NULL, `label` = '抵扣券×10' WHERE `threshold_count` = 50;
UPDATE `lottery_collect_milestone` SET `reward_type` = 'VOUCHER', `reward_value` = 20,  `alt_reward_value` = NULL, `label` = '抵扣券×20' WHERE `threshold_count` = 80;

-- 抵扣券：阶梯折扣（10 / 9.5 / 9 / 8.5 / 8 星辉每张），去掉无意义的限量
UPDATE `starlight_shop_item` SET `stock_remaining` = -1 WHERE `reward_type` IN ('LOTTERY_VOUCHER', 'MAKEUP_CARD');
UPDATE `starlight_shop_item` SET `price_starlight` = 95  WHERE `reward_type` = 'LOTTERY_VOUCHER' AND `reward_value` = 10;
UPDATE `starlight_shop_item` SET `price_starlight` = 425 WHERE `reward_type` = 'LOTTERY_VOUCHER' AND `reward_value` = 50;
UPDATE `starlight_shop_item` SET `price_starlight` = 800 WHERE `reward_type` = 'LOTTERY_VOUCHER' AND `reward_value` = 100;

-- 补签卡 30 张档定价回调
UPDATE `starlight_shop_item` SET `price_starlight` = 1200 WHERE `reward_type` = 'MAKEUP_CARD' AND `reward_value` = 30;

-- AI 额度重置卡是全商城唯一有真实调用成本的商品：改为每周限购 2 张，去掉日限
UPDATE `starlight_shop_item` SET `daily_limit` = 0, `weekly_limit` = 2 WHERE `reward_type` = 'QUOTA_RESET';

-- ============================================================
-- 背包：兑换与中奖的"卡片类"奖品不再立即生效，先入背包由用户择时使用
-- 积分与萌星辉是流水型货币，仍然即时到账，进背包反而别扭
-- ============================================================
CREATE TABLE IF NOT EXISTS `user_bag_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '归属用户',
  `source` varchar(16) NOT NULL COMMENT '来源 EXCHANGE兑换 LOTTERY抽奖',
  `source_ref_id` bigint DEFAULT NULL COMMENT '来源单据ID 兑换记录/抽奖记录',
  `item_name` varchar(64) NOT NULL COMMENT '展示名',
  `reward_type` varchar(32) NOT NULL COMMENT 'LOTTERY_VOUCHER/MAKEUP_CARD/QUOTA_RESET/VIP_DAYS/GOODS',
  `reward_value` int NOT NULL DEFAULT '0' COMMENT '数量或天数',
  `vip_tier` tinyint DEFAULT NULL COMMENT '会员档位 1PRO 2MAX',
  `use_status` tinyint NOT NULL DEFAULT '0' COMMENT '0未使用 1已使用 2待发放(实物)',
  `use_time` datetime DEFAULT NULL COMMENT '使用时间',
  `grant_summary` varchar(128) DEFAULT NULL COMMENT '发放结果摘要',
  `idempotency_key` varchar(128) NOT NULL COMMENT '幂等键',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bag_user_idem` (`user_id`,`idempotency_key`),
  KEY `idx_bag_user_status` (`user_id`,`delete_state`,`use_status`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户背包';

-- 安慰奖原本 prize_value=0，实际什么都不发（只有萌星辉），名不副实。改为发 3 张抵扣券
UPDATE `lottery_prize` SET `prize_value` = 3 WHERE `id` = 4 AND `prize_type` = 3;
UPDATE `lottery_prize` SET `name` = '安慰奖·抵扣券×3' WHERE `id` = 4 AND `prize_type` = 3;
