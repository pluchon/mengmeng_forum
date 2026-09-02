-- 一、Tavily 联网检索入账
--
-- 它是站内唯一按次计费的外部服务（$0.008/次），却是唯一完全不进账的一项。
-- 不给它单独设配额——它是 agent 手里的工具，不是用户能直接点的功能——
-- 但成本要计入周期额度，否则 ¥6 / ¥10.9 / ¥20.9 三个闸门是虚的。
--
-- 单价按 $0.008 × 7.3 ≈ ¥0.058 记；汇率变动时改这一行即可。
INSERT INTO `forum_ai_model_price`
    (`model_code`, `provider`, `bill_unit`, `price_yuan`, `vip_only`, `enabled`, `remark`)
VALUES
    ('tavily-search', 'tavily', 'per_call', 0.058000, 0, 1, 'Tavily 联网检索，按次计费')
ON DUPLICATE KEY UPDATE
    `price_yuan` = VALUES(`price_yuan`),
    `enabled`    = VALUES(`enabled`),
    `remark`     = VALUES(`remark`);

-- 二、停用不再调用的历史模型
--
-- 这四个 model_code 代码里已经没有任何引用，但价格行仍是 enabled=1，
-- 也就是「过时的价格 + 生效状态」——哪天配置里写错一个模型名，
-- 就会按这些过时单价结算。停用而不删除：历史用量日志还引用着它们。
UPDATE `forum_ai_model_price`
   SET `enabled` = 0,
       `remark` = CONCAT(IFNULL(`remark`, ''), '（已停用：代码无引用）')
 WHERE `model_code` IN ('qwen3.6-flash', 'tongyi-embedding-vision-flash',
                        'z-image-turbo', 'wanx2.1-t2i-plus')
   AND `enabled` = 1;
