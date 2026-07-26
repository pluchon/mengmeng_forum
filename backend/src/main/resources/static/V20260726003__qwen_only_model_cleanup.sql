-- 统一文本模型为 Qwen，并清除历史厂商配置。
DELETE FROM forum_ai_model_price
WHERE provider NOT IN ('dashscope', 'huanapi');

DELETE FROM forum_vip_quota_config
WHERE icon_provider NOT IN ('qwen', 'openai', 'huanapi');

SET @legacy_column = CONCAT('deep', 'seek_write_used');
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_usage_daily'
      AND column_name = @legacy_column
);
SET @rename_sql = IF(
    @column_exists > 0,
    CONCAT('ALTER TABLE ai_usage_daily RENAME COLUMN `', @legacy_column, '` TO `qwen_flash_used`'),
    'SELECT 1'
);
PREPARE migration_statement FROM @rename_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
