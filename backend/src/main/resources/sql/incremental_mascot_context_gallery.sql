-- 看板娘：联网图集与上下文压缩事件的扩展元数据
SET @schema_name = DATABASE();
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'forum_companion_message'
      AND column_name = 'metadata_json'
);
SET @sql = IF(
    @column_exists = 0,
    'ALTER TABLE forum_companion_message ADD COLUMN metadata_json MEDIUMTEXT NULL COMMENT ''消息扩展元数据（联网图集、上下文摘要来源等）'' AFTER image_url',
    'SELECT 1'
);
PREPARE mascot_context_gallery_stmt FROM @sql;
EXECUTE mascot_context_gallery_stmt;
DEALLOCATE PREPARE mascot_context_gallery_stmt;
