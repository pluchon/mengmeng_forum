-- 下线已停止提供的文本模型。
-- 仅关闭价格与会员配额配置，保留历史调用、计费和用量记录。

START TRANSACTION;

UPDATE `forum_ai_model_price`
SET `enabled` = 0,
    `update_time` = CURRENT_TIMESTAMP
WHERE `model_code` IN (
    'gemini-3.1-pro',
    'claude-haiku-4-5',
    'claude-sonnet-4-6'
)
  AND `enabled` <> 0;

UPDATE `forum_vip_quota_config`
SET `enabled` = 0
WHERE `quota_key` IN (
    'token_gemini_deep',
    'token_claude_haiku',
    'token_claude_sonnet'
)
  AND `enabled` <> 0;

COMMIT;
