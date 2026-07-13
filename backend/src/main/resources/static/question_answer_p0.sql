-- 问答帖与最佳答案 P0 增量结构。
-- 执行前提：article 与 article_reply 已存在；本脚本只允许向前执行一次。

ALTER TABLE `article`
    ADD COLUMN `article_type` tinyint NOT NULL DEFAULT 0 COMMENT '帖子业务类型: 0普通帖 1问答帖' AFTER `content_type`,
    ADD COLUMN `question_status` tinyint DEFAULT NULL COMMENT '问答状态: 0待解决 1已解决 2已关闭; 普通帖为空' AFTER `article_type`,
    ADD COLUMN `accepted_reply_id` bigint DEFAULT NULL COMMENT '最佳答案对应的一级回答ID' AFTER `question_status`,
    ADD INDEX `idx_article_question_filter` (`article_type`, `question_status`, `status`, `delete_state`, `update_time`);

UPDATE `article`
SET `article_type` = 0,
    `question_status` = NULL,
    `accepted_reply_id` = NULL
WHERE `article_type` IS NULL;
