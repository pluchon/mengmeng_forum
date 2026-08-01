-- 积分钱包：从 user.points 拆出，归属 economy 域
CREATE TABLE IF NOT EXISTS `points_wallet` (
                                               `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                                               `user_id` BIGINT NOT NULL COMMENT '用户ID（逻辑关联 auth.user，无跨库外键）',
                                               `balance` INT NOT NULL DEFAULT 0 COMMENT '当前积分余额',
                                               `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
                                               `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                               `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                               `delete_state` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
                                               PRIMARY KEY (`id`),
                                               UNIQUE KEY `uk_points_wallet_user_id` (`user_id`),
                                               KEY `idx_points_wallet_delete_state` (`delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分钱包（economy 权威）';

-- 从历史 user.points 回填（幂等：已存在则跳过）
INSERT INTO `points_wallet` (`user_id`, `balance`, `version`, `delete_state`)
SELECT u.`id`, IFNULL(u.`points`, 0), 0, 0
FROM `user` u
WHERE u.`delete_state` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `points_wallet` w WHERE w.`user_id` = u.`id` AND w.`delete_state` = 0
);

-- VIP 订阅：从 user.vip_tier / user.vip_expire_at 拆出
CREATE TABLE IF NOT EXISTS `user_vip_subscription` (
                                                       `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                                                       `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                                       `vip_tier` TINYINT NOT NULL DEFAULT 0 COMMENT 'VIP档位: 0普通 1PRO 2MAX',
                                                       `vip_expire_at` DATETIME NULL COMMENT 'VIP到期时间',
                                                       `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                                       `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                       `delete_state` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
                                                       PRIMARY KEY (`id`),
                                                       UNIQUE KEY `uk_user_vip_subscription_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户VIP订阅（economy 权威）';

INSERT INTO `user_vip_subscription` (`user_id`, `vip_tier`, `vip_expire_at`, `delete_state`)
SELECT u.`id`, IFNULL(u.`vip_tier`, 0), u.`vip_expire_at`, 0
FROM `user` u
WHERE u.`delete_state` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `user_vip_subscription` v WHERE v.`user_id` = u.`id` AND v.`delete_state` = 0
);

-- 抽奖硬保底计数：从 user.lottery_pity_draws 拆出
CREATE TABLE IF NOT EXISTS `user_lottery_pity` (
                                                   `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                                                   `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                                   `pity_draws` INT NOT NULL DEFAULT 0 COMMENT '距上次神秘大奖已连续开奖次数',
                                                   `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                                   `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                   `delete_state` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
                                                   PRIMARY KEY (`id`),
                                                   UNIQUE KEY `uk_user_lottery_pity_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='抽奖硬保底计数（economy 权威）';

INSERT INTO `user_lottery_pity` (`user_id`, `pity_draws`, `delete_state`)
SELECT u.`id`, IFNULL(u.`lottery_pity_draws`, 0), 0
FROM `user` u
WHERE u.`delete_state` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `user_lottery_pity` p WHERE p.`user_id` = u.`id` AND p.`delete_state` = 0
);
