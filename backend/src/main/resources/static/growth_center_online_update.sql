-- 成长中心 P0 线上增量 SQL：仅新增事实表，不修改既有题库与会员历史数据。

CREATE TABLE IF NOT EXISTS `user_growth_profile` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '成长档案ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `formal_state` tinyint NOT NULL DEFAULT 0 COMMENT '正式用户状态: 0非正式 1正式',
    `experience` int NOT NULL DEFAULT 0 COMMENT '成长经验',
    `growth_level` int NOT NULL DEFAULT 1 COMMENT '成长等级',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_growth_profile_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户成长档案';

CREATE TABLE IF NOT EXISTS `growth_challenge` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '挑战ID',
    `challenge_code` varchar(40) NOT NULL COMMENT '挑战编码',
    `challenge_type` varchar(30) NOT NULL COMMENT '挑战类型',
    `title` varchar(80) NOT NULL COMMENT '挑战标题',
    `description` varchar(500) DEFAULT NULL COMMENT '挑战说明',
    `bank_id` bigint NOT NULL COMMENT '关联题库ID',
    `question_count` int NOT NULL DEFAULT 10 COMMENT '抽题数',
    `passing_score` int NOT NULL DEFAULT 80 COMMENT '及格分',
    `max_attempts_per_day` int NOT NULL DEFAULT 3 COMMENT '每日最大尝试数',
    `experience_reward` int NOT NULL DEFAULT 0 COMMENT '通过经验奖励',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用: 0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_growth_challenge_code` (`challenge_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成长挑战定义';

CREATE TABLE IF NOT EXISTS `growth_challenge_attempt` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '挑战尝试ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `challenge_id` bigint NOT NULL COMMENT '挑战ID',
    `attempt_no` int NOT NULL COMMENT '尝试序号',
    `status` varchar(20) NOT NULL COMMENT '尝试状态',
    `question_ids_json` json NOT NULL COMMENT '本次题目ID',
    `answers_json` json DEFAULT NULL COMMENT '用户答案',
    `score` int DEFAULT NULL COMMENT '得分',
    `started_at` datetime NOT NULL COMMENT '开始时间',
    `submitted_at` datetime DEFAULT NULL COMMENT '提交时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_growth_attempt_user_challenge_no` (`user_id`, `challenge_id`, `attempt_no`),
    KEY `idx_growth_attempt_user_challenge` (`user_id`, `challenge_id`, `delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成长挑战尝试记录';

CREATE TABLE IF NOT EXISTS `growth_experience_log` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '经验流水ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `source_type` varchar(30) NOT NULL COMMENT '经验来源类型',
    `source_business_id` bigint NOT NULL COMMENT '来源业务ID',
    `experience_delta` int NOT NULL COMMENT '经验变动',
    `remark` varchar(200) DEFAULT NULL COMMENT '备注',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_growth_experience_source` (`user_id`, `source_type`, `source_business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成长经验流水';

CREATE TABLE IF NOT EXISTS `growth_reward_record` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '奖励流水ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `challenge_id` bigint NOT NULL COMMENT '挑战ID',
    `reward_type` varchar(30) NOT NULL COMMENT '奖励类型',
    `reward_value` varchar(100) DEFAULT NULL COMMENT '奖励值',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_growth_reward_user_challenge_type` (`user_id`, `challenge_id`, `reward_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成长奖励流水';

-- 存量账号迁移为正式用户，避免上线时误锁既有社区能力。
INSERT INTO `user_growth_profile` (`user_id`, `formal_state`, `experience`, `growth_level`)
SELECT `id`, 1, 0, 1 FROM `user`
WHERE `delete_state` = 0
  AND NOT EXISTS (SELECT 1 FROM `user_growth_profile` p WHERE p.user_id = `user`.`id`);
