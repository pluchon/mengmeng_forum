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
