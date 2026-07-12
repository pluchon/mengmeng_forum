-- 考试题库模块线上增量 SQL
-- 用途：已有线上库首次上线考试题库功能时执行；已存在表时不会重建或清空数据。
-- 注意：不要在线上执行 create.sql，它会重建数据库。

CREATE TABLE IF NOT EXISTS `exam_question_bank` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '题库ID',
    `user_id` bigint NOT NULL COMMENT '创建用户ID',
    `subject` varchar(100) NOT NULL COMMENT '考试科目',
    `source_name` varchar(255) NOT NULL COMMENT '来源文件名',
    `total_count` int NOT NULL DEFAULT 0 COMMENT '总题数',
    `choice_count` int NOT NULL DEFAULT 0 COMMENT '选择题数量',
    `judgement_count` int NOT NULL DEFAULT 0 COMMENT '判断题数量',
    `subjective_count` int NOT NULL DEFAULT 0 COMMENT '主观题数量',
    `warnings_json` json DEFAULT NULL COMMENT '解析警告JSON',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    KEY `idx_user_subject_time` (`user_id`, `subject`, `delete_state`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试题库主表';

CREATE TABLE IF NOT EXISTS `exam_question` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '题目ID',
    `bank_id` bigint NOT NULL COMMENT '题库ID',
    `question_order` int NOT NULL COMMENT '题目顺序',
    `source_no` varchar(30) DEFAULT NULL COMMENT '原文题号',
    `section_name` varchar(100) DEFAULT NULL COMMENT '原文章节或分组',
    `question_type` varchar(20) NOT NULL COMMENT '题目类型',
    `stem` text NOT NULL COMMENT '题干',
    `options_json` json DEFAULT NULL COMMENT '选项JSON',
    `standard_answer` text COMMENT '标准答案',
    `explanation` text COMMENT '解析',
    `answer_inferred_from_user` tinyint NOT NULL DEFAULT 0 COMMENT '答案是否从用户答案推断',
    `needs_option_review` tinyint NOT NULL DEFAULT 0 COMMENT '是否需要人工复核选项',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    KEY `idx_bank_order` (`bank_id`, `delete_state`, `question_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试题库题目表';

CREATE TABLE IF NOT EXISTS `exam_question_user_progress` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '进度ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `bank_id` bigint NOT NULL COMMENT '题库ID',
    `question_id` bigint NOT NULL COMMENT '题目ID',
    `answer_text` varchar(1000) DEFAULT NULL COMMENT '用户答案',
    `answered` tinyint NOT NULL DEFAULT 0 COMMENT '是否已作答: 0否 1是',
    `correct` tinyint DEFAULT NULL COMMENT '是否答对: 0否 1是',
    `wrong` tinyint NOT NULL DEFAULT 0 COMMENT '是否错题: 0否 1是',
    `focus` tinyint NOT NULL DEFAULT 0 COMMENT '是否重点记忆: 0否 1是',
    `judge_score` int DEFAULT NULL COMMENT '主观题评分',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_exam_progress_user_question` (`user_id`, `question_id`),
    KEY `idx_exam_progress_user_bank` (`user_id`, `bank_id`, `delete_state`),
    KEY `idx_exam_progress_user_focus` (`user_id`, `focus`, `delete_state`),
    KEY `idx_exam_progress_user_wrong` (`user_id`, `wrong`, `delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试题库用户答题进度表';

UPDATE `exam_question_bank`
SET `user_id` = 0,
    `update_time` = CURRENT_TIMESTAMP
WHERE `delete_state` = 0
  AND `user_id` <> 0;
