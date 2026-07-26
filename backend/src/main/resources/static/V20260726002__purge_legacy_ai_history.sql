-- 旧 Python AI 路由停用后，不再保留旧看板娘会话与 AI 计费历史。
-- 保留表结构：新 Gateway 的看板娘、配额和计费流程仍依赖这些表继续写入。

DELETE FROM forum_companion_message;
DELETE FROM forum_companion_session;
DELETE FROM forum_ai_call_record;
DELETE FROM forum_ai_usage_log;
DELETE FROM forum_ai_model_usage_daily;
DELETE FROM ai_usage_daily;
