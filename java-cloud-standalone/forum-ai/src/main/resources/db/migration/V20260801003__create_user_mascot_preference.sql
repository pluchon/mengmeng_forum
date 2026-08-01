-- 看板娘偏好：从 user.mascot_model_id 拆出，归属 ai 域
CREATE TABLE IF NOT EXISTS `user_mascot_preference` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `mascot_model_id` BIGINT NULL COMMENT 'forum_mascot_model.id',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_state` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_mascot_preference_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户看板娘偏好（ai 权威）';

INSERT INTO `user_mascot_preference` (`user_id`, `mascot_model_id`, `delete_state`)
SELECT u.`id`, u.`mascot_model_id`, 0
FROM `user` u
WHERE u.`delete_state` = 0
  AND u.`mascot_model_id` IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `user_mascot_preference` m WHERE m.`user_id` = u.`id` AND m.`delete_state` = 0
  );
