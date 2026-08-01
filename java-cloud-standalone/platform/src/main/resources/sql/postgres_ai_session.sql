-- PostgreSQL：AI 多轮会话（封面配图要点 / 看板娘绘画等 LangGraph 状态）
-- 在目标库中执行；与 MySQL 各领域独立库仅通过 user_id 逻辑关联。
-- 本文件可重复执行；已有库直接重复执行即可补齐扩展、索引与触发器。
-- 开发清空 LangGraph checkpoint：见 ai-server/clients/checkpoint.py（PostgresSaver auto_setup）。
-- 看板娘对话历史在 MySQL forum_ai_db 的 forum_companion_*，需重跑 ai/server 的 db/create.sql 或 TRUNCATE 该两表。
--
-- LangGraph PostgresSaver 会在同一库自动创建 checkpoint_* 系统表，与本文件业务表互不冲突。
-- MySQL 的创作工作区、任务状态与看板娘会话由 forum_ai_db 管理；PostgreSQL 仅保存 LangGraph checkpoint 与本文件定义的会话索引。
--
-- 可选独立 schema：
--   CREATE SCHEMA IF NOT EXISTS forum_ai;
--   SET search_path TO forum_ai;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS ai_chat_session (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             BIGINT NOT NULL,
    scene               VARCHAR(32) NOT NULL,
    related_article_id  BIGINT,
    model_tier          VARCHAR(32),
    title               VARCHAR(200),
    client_request_id   VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_session_user_time ON ai_chat_session (user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_session_user_scene ON ai_chat_session (user_id, scene, updated_at DESC);

-- 已有库补列（新库 CREATE TABLE 已含该列，可重复执行）
ALTER TABLE ai_chat_session ADD COLUMN IF NOT EXISTS client_request_id VARCHAR(64);

-- 客户端 requestId 重试时不重复创建会话（client_request_id 为空时不参与约束）
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_session_user_scene_request
    ON ai_chat_session (user_id, scene, client_request_id)
    WHERE client_request_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS ai_chat_message (
    id          BIGSERIAL PRIMARY KEY,
    session_id  UUID NOT NULL REFERENCES ai_chat_session(id) ON DELETE CASCADE,
    role        VARCHAR(16) NOT NULL,
    content     TEXT NOT NULL,
    meta        JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_msg_session ON ai_chat_message (session_id, id);

-- 自动维护 ai_chat_session.updated_at
CREATE OR REPLACE FUNCTION touch_ai_chat_session_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE ai_chat_session SET updated_at = NOW() WHERE id = NEW.session_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ai_chat_message_touch_session ON ai_chat_message;
CREATE TRIGGER trg_ai_chat_message_touch_session
    AFTER INSERT ON ai_chat_message
    FOR EACH ROW
    EXECUTE FUNCTION touch_ai_chat_session_updated_at();
