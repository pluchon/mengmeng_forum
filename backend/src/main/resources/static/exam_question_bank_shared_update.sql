-- 考试题库共享化增量 SQL
-- 目的：题库内容全站共享，用户个人答题进度仍保留在 exam_question_user_progress。
-- 执行前提：exam_question_bank / exam_question / exam_question_user_progress 已存在。

UPDATE `exam_question_bank`
SET `user_id` = 0,
    `update_time` = CURRENT_TIMESTAMP
WHERE `delete_state` = 0
  AND `user_id` <> 0;
