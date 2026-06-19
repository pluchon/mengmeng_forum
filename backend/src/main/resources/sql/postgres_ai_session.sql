-- PostgreSQL：AI 多轮会话（封面配图要点 / 看板娘绘画等 LangGraph 状态）
-- 在目标库中执行；与 MySQL forum_db 仅通过 user_id 逻辑关联。
-- 本文件仅建表，无示例数据。勿与 create.sql 混跑。
-- 开发清空 LangGraph checkpoint：见同目录 postgres_reset_dev.sql（docker exec psql）。
-- 看板娘对话历史在 MySQL forum_companion_*，需整库重跑 create.sql 或 TRUNCATE 该两表。
--
-- 可选独立 schema：
--   CREATE SCHEMA IF NOT EXISTS forum_ai;
--   SET search_path TO forum_ai;

CREATE TABLE IF NOT EXISTS ai_chat_session (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             BIGINT NOT NULL,
    scene               VARCHAR(32) NOT NULL,
    related_article_id  BIGINT,
    model_tier          VARCHAR(32),
    title               VARCHAR(200),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_session_user_time ON ai_chat_session (user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS ai_chat_message (
    id          BIGSERIAL PRIMARY KEY,
    session_id  UUID NOT NULL REFERENCES ai_chat_session(id) ON DELETE CASCADE,
    role        VARCHAR(16) NOT NULL,
    content     TEXT NOT NULL,
    meta        JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_msg_session ON ai_chat_message (session_id, id);
