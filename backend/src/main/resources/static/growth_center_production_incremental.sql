-- 成长中心线上完整增量 SQL。
-- 对照 create.sql 与当前数据库结构整理，可重复执行，不覆盖历史挑战、答题、奖励和会员记录。

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

-- 存量账号迁移为正式用户，避免上线后锁住既有社区能力。
INSERT INTO `user_growth_profile` (`user_id`, `formal_state`, `experience`, `growth_level`)
SELECT u.`id`, 1, 0, 1
FROM `user` u
WHERE u.`delete_state` = 0
  AND NOT EXISTS (
      SELECT 1 FROM `user_growth_profile` p WHERE p.`user_id` = u.`id`
  );

-- 按当前非线性成长阈值校准等级快照。
UPDATE `user_growth_profile`
SET `growth_level` = CASE
        WHEN `experience` >= 1500 THEN 6
        WHEN `experience` >= 900 THEN 5
        WHEN `experience` >= 500 THEN 4
        WHEN `experience` >= 250 THEN 3
        WHEN `experience` >= 100 THEN 2
        ELSE 1
    END,
    `update_time` = CURRENT_TIMESTAMP
WHERE `delete_state` = 0
  AND `growth_level` <> CASE
        WHEN `experience` >= 1500 THEN 6
        WHEN `experience` >= 900 THEN 5
        WHEN `experience` >= 500 THEN 4
        WHEN `experience` >= 250 THEN 3
        WHEN `experience` >= 100 THEN 2
        ELSE 1
    END;

-- 创建成长中心官方题库。
INSERT INTO `exam_question_bank`
(`user_id`, `subject`, `source_name`, `total_count`, `choice_count`, `judgement_count`, `subjective_count`, `warnings_json`, `delete_state`)
SELECT 0, '成长中心·新人试炼', 'growth-center-formal-user', 10, 10, 0, 0, JSON_ARRAY(), 0
WHERE NOT EXISTS (
    SELECT 1 FROM `exam_question_bank`
    WHERE `user_id` = 0 AND `subject` = '成长中心·新人试炼' AND `delete_state` = 0
);

INSERT INTO `exam_question_bank`
(`user_id`, `subject`, `source_name`, `total_count`, `choice_count`, `judgement_count`, `subjective_count`, `warnings_json`, `delete_state`)
SELECT 0, '成长中心·会员体验', 'growth-center-vip-trial-900', 10, 10, 0, 0, JSON_ARRAY(), 0
WHERE NOT EXISTS (
    SELECT 1 FROM `exam_question_bank`
    WHERE `user_id` = 0 AND `subject` = '成长中心·会员体验' AND `delete_state` = 0
);

SET @formal_bank := (
    SELECT `id` FROM `exam_question_bank`
    WHERE `user_id` = 0 AND `subject` = '成长中心·新人试炼' AND `delete_state` = 0
    ORDER BY `id` DESC LIMIT 1
);
SET @trial_bank := (
    SELECT `id` FROM `exam_question_bank`
    WHERE `user_id` = 0 AND `subject` = '成长中心·会员体验' AND `delete_state` = 0
    ORDER BY `id` DESC LIMIT 1
);

INSERT INTO `exam_question`
(`bank_id`, `question_order`, `source_no`, `section_name`, `question_type`, `stem`, `options_json`, `standard_answer`, `explanation`, `answer_inferred_from_user`, `needs_option_review`, `delete_state`)
SELECT @formal_bank, q.source_no, q.source_no, '社区规则', 'SINGLE', q.stem,
       JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', q.a), JSON_OBJECT('label', 'B', 'text', q.b)),
       'A', q.explanation, 0, 0, 0
FROM (
    SELECT 1 source_no, '发现违规内容时，正确的做法是？' stem, '使用举报入口并提供真实说明' a, '在评论区攻击对方' b, '维护社区安全' explanation UNION ALL
    SELECT 2, '发布他人隐私信息是否允许？', '不允许，应尊重他人隐私', '允许，只要内容有热度', '保护隐私是基本规则' UNION ALL
    SELECT 3, '遇到陌生链接时应当？', '谨慎核实来源，不随意输入账号信息', '立刻点击并转发', '防范账号风险' UNION ALL
    SELECT 4, '评论交流应遵循什么原则？', '友善、就事论事', '人身攻击更有说服力', '文明交流' UNION ALL
    SELECT 5, '转载社区内容前应当？', '确认授权并注明来源', '直接复制即可', '尊重原创' UNION ALL
    SELECT 6, '账号密码应当？', '妥善保管且不与他人共用', '告诉陌生网友', '账号安全' UNION ALL
    SELECT 7, '发现账号异常登录时应当？', '及时修改密码并查看登录记录', '继续忽略', '及时处置风险' UNION ALL
    SELECT 8, '发布内容时应当？', '遵守法律和社区规则', '为了流量可以造谣', '内容责任' UNION ALL
    SELECT 9, '与他人意见不同可以？', '理性表达观点', '恶意骚扰对方', '尊重不同意见' UNION ALL
    SELECT 10, '社区功能遇到问题时可以？', '查看帮助或反馈问题', '发布无关攻击内容', '合理反馈'
) q
WHERE NOT EXISTS (
    SELECT 1 FROM `exam_question` e
    WHERE e.`bank_id` = @formal_bank AND e.`question_order` = q.source_no AND e.`delete_state` = 0
);

INSERT INTO `exam_question`
(`bank_id`, `question_order`, `source_no`, `section_name`, `question_type`, `stem`, `options_json`, `standard_answer`, `explanation`, `answer_inferred_from_user`, `needs_option_review`, `delete_state`)
SELECT @trial_bank, q.source_no, q.source_no, '会员体验', 'SINGLE', q.stem,
       JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', q.a), JSON_OBJECT('label', 'B', 'text', q.b)),
       'A', q.explanation, 0, 0, 0
FROM (
    SELECT 1 source_no, '会员体验有效期是？' stem, '7 天' a, '永久有效' b, '体验权益有明确期限' explanation UNION ALL
    SELECT 2, '体验会员可以领取几次？', '每个账号一次', '每天一次', '避免重复领取' UNION ALL
    SELECT 3, '体验会员的模型额度？', '使用独立体验额度', '等同完整付费额度', '体验额度独立管理' UNION ALL
    SELECT 4, '已有有效付费会员能否领取体验？', '不能', '可以叠加', '避免覆盖付费权益' UNION ALL
    SELECT 5, '会员功能应当如何使用？', '遵守站内规则合理使用', '用于违规内容', '权益也需遵守规则' UNION ALL
    SELECT 6, '体验到期后会？', '自动失效', '自动续期', '体验不续期' UNION ALL
    SELECT 7, '模型用量达到体验额度后？', '等待重置或按规则使用其他能力', '绕过限制', '额度受系统控制' UNION ALL
    SELECT 8, '会员中心主要用于？', '查看权益和额度', '修改他人账号', '权益信息透明展示' UNION ALL
    SELECT 9, '体验挑战通过后获得？', '7 天 TRIAL_900 体验', '永久 MAX', '奖励与挑战匹配' UNION ALL
    SELECT 10, '会员体验挑战的目的？', '了解站内会员能力', '绕过付费规则', '合理体验权益'
) q
WHERE NOT EXISTS (
    SELECT 1 FROM `exam_question` e
    WHERE e.`bank_id` = @trial_bank AND e.`question_order` = q.source_no AND e.`delete_state` = 0
);

INSERT INTO `growth_challenge`
(`challenge_code`, `challenge_type`, `title`, `description`, `bank_id`, `question_count`, `passing_score`, `max_attempts_per_day`, `experience_reward`, `enabled`, `delete_state`)
SELECT 'FORMAL_USER', 'FORMAL_USER', '新人试炼', '完成社区规则与安全基础题，获得正式用户资格。', @formal_bank, 10, 80, 3, 100, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM `growth_challenge` WHERE `challenge_code` = 'FORMAL_USER'
);

INSERT INTO `growth_challenge`
(`challenge_code`, `challenge_type`, `title`, `description`, `bank_id`, `question_count`, `passing_score`, `max_attempts_per_day`, `experience_reward`, `enabled`, `delete_state`)
SELECT 'VIP_TRIAL_900', 'VIP_TRIAL_900', '会员体验挑战', '通过后获得一次 7 天 TRIAL_900 会员体验。', @trial_bank, 10, 80, 3, 80, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM `growth_challenge` WHERE `challenge_code` = 'VIP_TRIAL_900'
);
