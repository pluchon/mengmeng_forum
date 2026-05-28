-- 看板娘生图 URL 可能为长外链；业务侧已转存 OSS，此迁移仅放宽字段以防历史数据
ALTER TABLE `forum_companion_message`
    MODIFY COLUMN `image_url` varchar(1024) DEFAULT NULL COMMENT '生图URL(OSS)';
