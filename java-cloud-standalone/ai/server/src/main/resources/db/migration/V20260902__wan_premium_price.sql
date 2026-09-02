-- 进阶档生图 wan2.7-image-pro 打通：补上单价行。
--
-- 没有这一行的话，calcYuan 找不到单价会走兜底（按 flash 的 token 单价结算），
-- 一张进阶图几乎算不出钱来，额度闸门就成了摆设。
--
-- 额度口径与金额口径不同：金额按「张数 × ¥0.50」，额度按两张扣
-- （见 AiPointsBillingService.PREMIUM_WAN_UNITS）。
INSERT INTO `forum_ai_model_price`
    (`model_code`, `provider`, `bill_unit`, `price_yuan`, `vip_only`, `enabled`, `remark`)
VALUES
    ('wan2.7-image-pro', 'dashscope', 'per_image', 0.500000, 1, 1, '万相2.7进阶生图，额度按两张扣')
ON DUPLICATE KEY UPDATE
    `price_yuan` = VALUES(`price_yuan`),
    `vip_only`   = VALUES(`vip_only`),
    `enabled`    = VALUES(`enabled`),
    `remark`     = VALUES(`remark`);
