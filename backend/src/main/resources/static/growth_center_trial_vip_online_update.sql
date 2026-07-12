-- TRIAL_900 体验会员事实记录；付费会员与体验会员通过来源记录区分。
CREATE TABLE IF NOT EXISTS `vip_trial_entitlement` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '体验会员ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `trial_code` varchar(30) NOT NULL COMMENT '体验编码',
    `status` varchar(20) NOT NULL COMMENT '状态: ACTIVE EXPIRED SUPERSEDED',
    `expire_at` datetime NOT NULL COMMENT '体验到期时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_vip_trial_user_code` (`user_id`, `trial_code`),
    KEY `idx_vip_trial_active` (`user_id`, `status`, `expire_at`, `delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='体验会员权益';
